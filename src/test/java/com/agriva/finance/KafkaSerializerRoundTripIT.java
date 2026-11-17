package com.agriva.finance;

import com.agriva.TestcontainersConfiguration;
import com.agriva.finance.kafka.producer.ExpenseProducer;
import com.agriva.finance.model.dto.request.ExpenseRequest;
import com.agriva.finance.model.dto.request.ExpenseSubmissionEvent;
import com.agriva.finance.model.dto.request.InvestmentRequest;
import com.agriva.finance.model.entity.StagedExpense;
import com.agriva.finance.model.enums.ExpenseCategory;
import com.agriva.finance.repository.ExpenseRepository;
import com.agriva.finance.repository.InvestmentRepository;
import com.agriva.finance.repository.StagedExpenseRepository;
import com.agriva.farming.repository.FarmRepository;
import com.agriva.identity.models.dto.request.MasterRegisterRequest;
import com.agriva.identity.models.dto.response.AuthResponse;
import com.agriva.identity.repository.AuthRepository;
import com.agriva.identity.service.AuthService;
import com.agriva.personnel.models.enums.Gender;
import com.agriva.shared.kafka.topics.ExpenseKafkaConfig;
import com.agriva.shared.models.enums.Currency;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.math.BigDecimal;
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
class KafkaSerializerRoundTripIT {

    @Autowired ExpenseProducer expenseProducer;
    @Autowired ExpenseRepository expenseRepo;
    @Autowired StagedExpenseRepository stagedRepo;
    @Autowired InvestmentRepository investmentRepo;
    @Autowired AuthRepository authRepository;
    @Autowired FarmRepository farmRepository;
    @Autowired AuthService authService;
    @Autowired ObjectMapper objectMapper;
    @Autowired KafkaConnectionDetails kafkaConnectionDetails;

    UUID farmId;
    UUID userId;

    @BeforeEach
    void setUp() {
        stagedRepo.deleteAll();
        expenseRepo.deleteAll();
        investmentRepo.deleteAll();
        authRepository.deleteAll();
        farmRepository.deleteAll();

        MasterRegisterRequest reg = new MasterRegisterRequest();
        reg.setEmail("serializer@test.local");
        reg.setPassword("MasterPass1!");
        reg.setFarmName("Serializer Farm " + UUID.randomUUID());
        reg.setName("Serializer Master");
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
        userId = authRepository.findByEmail("serializer@test.local").orElseThrow().getId();

        InvestmentRequest inv = new InvestmentRequest();
        inv.setAmount(new BigDecimal("1000000.00"));
        inv.setCurrency(Currency.XAF);
        inv.setInvestorName("Serializer Investor");
        inv.setInvestmentDate(LocalDate.now());
        inv.setActivate(true);
    }

    @Test
    void expenseEvent_publishesThroughJsonSerializer_andReachesStaging() throws Exception {
        long dlqBefore = dlqEndOffset(ExpenseKafkaConfig.STAGING_DLQ_TOPIC);

        UUID submissionId = UUID.randomUUID();
        String payload = objectMapper.writeValueAsString(sampleRequest());
        ExpenseSubmissionEvent event = new ExpenseSubmissionEvent(
                submissionId, farmId, userId, "serializer@test.local",
                payload, LocalDateTime.now(), null, null);

        expenseProducer.publish(event);

        // Assertion 1: consumer body ran → staged row exists.
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            StagedExpense staged = stagedRepo.findByIdOnly(submissionId).orElse(null);
            assertThat(staged)
                    .as("consumer must persist the staged row within 20s; " +
                            "if absent, the producer/consumer serializers disagree")
                    .isNotNull();
        });

        // Assertion 2: no DLQ traffic — the message did not need to be recovered.
        long dlqAfter = dlqEndOffset(ExpenseKafkaConfig.STAGING_DLQ_TOPIC);
        assertThat(dlqAfter)
                .as("DLQ must not have moved; message was rejected by the error handler")
                .isEqualTo(dlqBefore);
    }

    private ExpenseRequest sampleRequest() {
        ExpenseRequest r = new ExpenseRequest();
        r.setReason("Serializer round-trip");
        r.setExpenseDate(LocalDate.now().minusDays(1));
        r.setQtyPurchased(1);
        r.setAmountPaid(new BigDecimal("150"));
        r.setExpectedAmount(new BigDecimal("150"));
        r.setCurrency(Currency.XAF);
        r.setCategory(ExpenseCategory.MISCELLANEOUS);
        return r;
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