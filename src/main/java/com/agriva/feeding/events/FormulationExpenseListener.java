package com.agriva.feeding.events;

import com.agriva.finance.kafka.producer.ExpenseProducer;
import com.agriva.finance.model.dto.request.ExpenseSubmissionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class FormulationExpenseListener {

    private final ExpenseProducer expenseProducer;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onExpenseReady(FormulationExpenseEvent event) {
        try {
            expenseProducer.publish(new ExpenseSubmissionEvent(
                    event.expenseSubmissionId(),
                    event.farmId(),
                    event.userId(),
                    null,
                    event.payloadJson(),
                    LocalDateTime.now(),
                    null,
                    null));
        } catch (Exception e) {
            log.error("CRITICAL: failed to publish {} expense submission {} for formulation {} "
                            + "after transaction commit. Formulation is committed; expense is NOT. "
                            + "Manual reconciliation required — search finance module for submission {}.",
                    event.reversal() ? "REVERSAL" : "",
                    event.expenseSubmissionId(),
                    event.formulationId(),
                    event.expenseSubmissionId(),
                    e);
            return;
        }
        log.info("Published {} expense submission {} for formulation {}",
                event.reversal() ? "REVERSAL" : "",
                event.expenseSubmissionId(),
                event.formulationId());
    }
}