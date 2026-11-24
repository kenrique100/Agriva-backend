package com.agriva.finance.scheduler;

import com.agriva.finance.model.dto.request.IncomeRequest;
import com.agriva.finance.model.entity.Income;
import com.agriva.finance.model.entity.StagedIncome;
import com.agriva.shared.models.enums.StagedStatus;
import com.agriva.finance.repository.IncomeRepository;
import com.agriva.finance.repository.StagedIncomeRepository;
import com.agriva.finance.utils.IncomeValidator;
import com.agriva.shared.lock.DistributedLockService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Component
public class StagedIncomeValidator extends AbstractStagedValidator<StagedIncome, IncomeRequest> {

    private final StagedIncomeRepository stagedRepo;
    private final IncomeRepository incomeRepo;
    private final IncomeValidator incomeValidator;

    @Value("${agriva.income.validator.batch-size:100}")          private int batchSize;
    @Value("${agriva.income.validator.max-attempts:4}")          private int maxAttempts;
    @Value("${agriva.income.validator.retry-delay-minutes:1}")   private long retryDelayMinutes;

    public StagedIncomeValidator(ObjectMapper objectMapper,
                                 TransactionTemplate transactionTemplate,
                                 DistributedLockService lock,
                                 StagedIncomeRepository stagedRepo,
                                 IncomeRepository incomeRepo,
                                 IncomeValidator incomeValidator) {
        super(objectMapper, transactionTemplate, lock);
        this.stagedRepo = stagedRepo;
        this.incomeRepo = incomeRepo;
        this.incomeValidator = incomeValidator;
    }

    @Scheduled(cron = "${agriva.income.validator.cron:0 * * * * *}")
    public void processStagedIncomes() {
        process();
    }

    @Override protected int  getBatchSize()          { return batchSize; }
    @Override protected int  getMaxAttempts()        { return maxAttempts; }
    @Override protected long getRetryDelayMinutes()  { return retryDelayMinutes; }

    @Override protected String typeName()   { return "income"; }
    @Override protected String getLockKey() { return "staged-income-validator"; }

    @Override protected Class<IncomeRequest> requestType() { return IncomeRequest.class; }

    @Override
    protected List<String> validateRequest(IncomeRequest request, UUID farmId) {
        return incomeValidator.validate(request, farmId);
    }

    @Override
    protected List<StagedIncome> fetchDueRows(int batchSize) {
        return stagedRepo.findDueForProcessing(
                LocalDateTime.now(), StagedStatus.PENDING, StagedStatus.RETRYING,
                PageRequest.of(0, batchSize));
    }

    @Override protected StagedIncome findForUpdate(UUID stagedId) {
        return stagedRepo.findByIdForUpdate(stagedId).orElse(null);
    }

    @Override protected void saveStaged(StagedIncome staged) {
        stagedRepo.save(staged);
    }

    @Override protected void setMovedReference(StagedIncome staged, UUID movedId) {
        staged.setMovedIncomeId(movedId);
    }

    @Override
    protected PromotionResult promote(StagedIncome staged, IncomeRequest request) {
        Income income = Income.builder()
                .farmId(staged.getFarmId())
                .transactionId(request.getTransactionId())
                .reason(request.getReason())
                .quantity(request.getQuantity())
                .amountReceived(request.getAmountReceived())
                .expectedAmount(request.getExpectedAmount())
                .receipt(request.getReceipt())
                .currency(request.getCurrency())
                .category(request.getCategory())
                .incomeDate(request.getIncomeDate())
                .createdByUserId(staged.getSubmittedByUserId())
                .build();
        income.calculateDueBalance();
        Income saved = incomeRepo.save(income);
        return PromotionResult.moved(saved.getId());
    }
}