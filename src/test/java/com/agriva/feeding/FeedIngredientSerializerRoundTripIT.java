package com.agriva.feeding;

import com.agriva.TestcontainersConfiguration;
import com.agriva.feeding.kafka.producer.FeedIngredientProducer;
import com.agriva.feeding.model.dto.request.FeedIngredientSubmissionEvent;
import com.agriva.feeding.model.dto.request.IngredientSubmissionPayload;
import com.agriva.feeding.model.entity.StagedFeedingIngredient;
import com.agriva.feeding.model.enums.IngredientUnit;
import com.agriva.feeding.repository.IngredientRepository;
import com.agriva.feeding.repository.IngredientStockRepository;
import com.agriva.feeding.repository.StagedFeedingIngredientRepository;
import com.agriva.farming.repository.FarmRepository;
import com.agriva.identity.models.dto.request.MasterRegisterRequest;
import com.agriva.identity.models.dto.response.AuthResponse;
import com.agriva.identity.repository.AuthRepository;
import com.agriva.identity.service.AuthService;
import com.agriva.personnel.models.enums.Gender;
import com.agriva.shared.kafka.topics.FeedIngredientKafkaConfig;
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
        "agriva.feeding.ingredient.validator.cron=0 0 0 1 1 *",
        "agriva.feeding.ingredient.consumer.immediate-processing.enabled=false"
})
class FeedIngredientSerializerRoundTripIT {

    @Autowired FeedIngredientProducer producer;
    @Autowired StagedFeedingIngredientRepository stagedRepo;
    @Autowired IngredientStockRepository stockRepo;
    @Autowired IngredientRepository ingredientRepo;
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
        stockRepo.deleteAll();
        ingredientRepo.deleteAll();
        authRepository.deleteAll();
        farmRepository.deleteAll();

        MasterRegisterRequest reg = new MasterRegisterRequest();
        reg.setEmail("serializer-feed@test.local");
        reg.setPassword("MasterPass1!");
        reg.setFarmName("Serializer Feed Farm " + UUID.randomUUID());
        reg.setName("Serializer Feed Master");
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
        userId = authRepository.findByEmail("serializer-feed@test.local").orElseThrow().getId();
    }

    @Test
    void feedIngredientEvent_publishesThroughJsonSerializer_andReachesStaging() throws Exception {
        long dlqBefore = dlqEndOffset(FeedIngredientKafkaConfig.STAGING_DLQ_TOPIC);

        UUID submissionId = UUID.randomUUID();
        IngredientSubmissionPayload payload = new IngredientSubmissionPayload(
                "Round-trip Soya", IngredientUnit.KG, new BigDecimal("100"),
                new BigDecimal("1200"), Currency.XAF, "Serializer Supplier", null);

        producer.publish(new FeedIngredientSubmissionEvent(
                submissionId, farmId, userId, "serializer-feed@test.local",
                objectMapper.writeValueAsString(payload),
                LocalDateTime.now(), null, null));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            StagedFeedingIngredient staged = stagedRepo.findByIdOnly(submissionId).orElse(null);
            assertThat(staged)
                    .as("consumer must persist the staged row within 20s; " +
                            "if absent, the producer/consumer serializers disagree")
                    .isNotNull();
        });

        long dlqAfter = dlqEndOffset(FeedIngredientKafkaConfig.STAGING_DLQ_TOPIC);
        assertThat(dlqAfter)
                .as("DLQ must not have moved; message was rejected by the error handler")
                .isEqualTo(dlqBefore);
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