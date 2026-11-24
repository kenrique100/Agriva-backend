package com.agriva.finance;

import com.agriva.TestcontainersConfiguration;
import com.agriva.finance.kafka.producer.ExpenseProducer;
import com.agriva.finance.kafka.producer.IncomeProducer;
import com.agriva.finance.model.dto.request.ExpenseSubmissionEvent;
import com.agriva.finance.model.dto.request.IncomeSubmissionEvent;
import com.agriva.finance.model.entity.StagedExpense;
import com.agriva.finance.model.entity.StagedIncome;
import com.agriva.finance.repository.ExpenseRepository;
import com.agriva.finance.repository.IncomeRepository;
import com.agriva.finance.repository.InvestmentRepository;
import com.agriva.finance.repository.StagedExpenseRepository;
import com.agriva.finance.repository.StagedIncomeRepository;
import com.agriva.farming.repository.FarmRepository;
import com.agriva.identity.models.dto.request.MasterRegisterRequest;
import com.agriva.identity.models.dto.response.AuthResponse;
import com.agriva.identity.repository.AuthRepository;
import com.agriva.identity.service.AuthService;
import com.agriva.personnel.models.enums.Gender;
import com.agriva.shared.kafka.topics.ExpenseKafkaConfig;
import com.agriva.shared.kafka.topics.IncomeKafkaConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaConnectionDetails;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {
        "agriva.expense.validator.cron=0 0 0 1 1 *",
        "agriva.income.validator.cron=0 0 0 1 1 *"
})
class StagingIdempotencyIT {

    @Autowired ExpenseProducer expenseProducer;
    @Autowired IncomeProducer incomeProducer;
    @Autowired StagedExpenseRepository stagedExpenseRepo;
    @Autowired StagedIncomeRepository stagedIncomeRepo;
    @Autowired ExpenseRepository expenseRepo;
    @Autowired IncomeRepository incomeRepo;
    @Autowired InvestmentRepository investmentRepo;
    @Autowired AuthRepository authRepository;
    @Autowired FarmRepository farmRepository;
    @Autowired AuthService authService;
    @Autowired KafkaConnectionDetails kafkaConnectionDetails;

    UUID farmId;
    UUID userId;

    @BeforeEach
    void setUp() {
        stagedExpenseRepo.deleteAll();
        stagedIncomeRepo.deleteAll();
        expenseRepo.deleteAll();
        incomeRepo.deleteAll();
        investmentRepo.deleteAll();
        authRepository.deleteAll();
        farmRepository.deleteAll();

        MasterRegisterRequest reg = new MasterRegisterRequest();
        reg.setEmail("idem@test.local");
        reg.setPassword("MasterPass1!");
        reg.setFarmName("Idem Farm " + UUID.randomUUID());
        reg.setName("Idem Master");
        reg.setGender(Gender.MALE);
        reg.setDateOfBirth(LocalDate.now().minusYears(35));
        reg.setPhoneNumber("+237600000000");
        reg.setCountry("Cameroon");
        reg.setState("Centre");
        reg.setCity("Yaoundé");
        reg.setAddress("Rue 1");
        reg.setPostalCode("00000");
        reg.setCurrency("XAF");
        reg.setUserAgreement(true);
        AuthResponse master = authService.registerMaster(reg);
        farmId = master.getFarmId();
        userId = authRepository.findByEmail("idem@test.local").orElseThrow().getId();
    }

    @Test
    void expenseEvent_deliveredTwice_onlyOneStagedRow_andNoDlqTraffic() {
        long dlqBefore = dlqEndOffset(ExpenseKafkaConfig.STAGING_DLQ_TOPIC);

        UUID submissionId = UUID.randomUUID();
        LocalDateTime submittedAt = LocalDateTime.now();
        ExpenseSubmissionEvent event = new ExpenseSubmissionEvent(
                submissionId, farmId, userId, "idem@test.local",
                "{\"reason\":\"idem\",\"expenseDate\":\"" + LocalDate.now().minusDays(1)
                        + "\",\"qtyPurchased\":1,\"amountPaid\":100,\"expectedAmount\":100,"
                        + "\"currency\":\"XAF\",\"category\":\"MISCELLANEOUS\"}",
                submittedAt, null, null);

        expenseProducer.publish(event);
        expenseProducer.publish(event);

        await().atMost(Duration.ofSeconds(20))
                .during(Duration.ofSeconds(6))          // hold the condition for 6s — enough
                .untilAsserted(() -> {                  // for any redelivery/retry/DLQ to happen
                    List<StagedExpense> rows = stagedExpenseRepo.findAllByEventId(submissionId);
                    assertThat(rows)
                            .as("exactly one staged row for submission %s", submissionId)
                            .hasSize(1);
                });

        long dlqAfter = dlqEndOffset(ExpenseKafkaConfig.STAGING_DLQ_TOPIC);
        assertThat(dlqAfter)
                .as("second delivery must be silently skipped by IdempotencyGuard, "
                        + "not rejected by PK violation and routed to DLQ")
                .isEqualTo(dlqBefore);
    }

    @Test
    void incomeEvent_deliveredTwice_onlyOneStagedRow_andNoDlqTraffic() {
        long dlqBefore = dlqEndOffset(IncomeKafkaConfig.STAGING_DLQ_TOPIC);

        UUID submissionId = UUID.randomUUID();
        LocalDateTime submittedAt = LocalDateTime.now();
        IncomeSubmissionEvent event = new IncomeSubmissionEvent(
                submissionId, farmId, userId, "idem@test.local",
                "{\"reason\":\"idem\",\"incomeDate\":\"" + LocalDate.now().minusDays(1)
                        + "\",\"quantity\":1,\"amountReceived\":100,\"expectedAmount\":100,"
                        + "\"currency\":\"XAF\",\"category\":\"MISCELLANEOUS\"}",
                submittedAt, null, null);

        incomeProducer.publish(event);
        incomeProducer.publish(event);

        await().atMost(Duration.ofSeconds(20))
                .during(Duration.ofSeconds(6))
                .untilAsserted(() -> {
                    List<StagedIncome> rows = stagedIncomeRepo.findAllByEventId(submissionId);
                    assertThat(rows)
                            .as("exactly one staged row for submission %s", submissionId)
                            .hasSize(1);
                });

        long dlqAfter = dlqEndOffset(IncomeKafkaConfig.STAGING_DLQ_TOPIC);
        assertThat(dlqAfter).isEqualTo(dlqBefore);
    }

    private long dlqEndOffset(String topic) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                kafkaConnectionDetails.getBootstrapServers().get(0));
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "dlq-probe-" + UUID.randomUUID());
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        try (KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(props)) {
            TopicPartition tp = new TopicPartition(topic, 0);
            Map<TopicPartition, Long> ends = consumer.endOffsets(List.of(tp));
            return ends.get(tp);
        }
    }
}