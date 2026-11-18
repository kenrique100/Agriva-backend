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
import com.agriva.feeding.scheduler.StagedFeedingIngredientValidator;
import com.agriva.farming.repository.FarmRepository;
import com.agriva.identity.models.dto.request.MasterRegisterRequest;
import com.agriva.identity.models.dto.response.AuthResponse;
import com.agriva.identity.repository.AuthRepository;
import com.agriva.identity.service.AuthService;
import com.agriva.personnel.models.enums.Gender;
import com.agriva.shared.lock.DistributedLockService;
import com.agriva.shared.models.enums.Currency;
import com.agriva.shared.models.enums.StagedStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {
        "agriva.feeding.ingredient.validator.cron=0 0 0 1 1 *",
        "agriva.feeding.ingredient.validator.retry-delay-minutes=0",
        "agriva.feeding.ingredient.consumer.immediate-processing.enabled=false"
})
class FeedIngredientValidatorLockIT {

    private static final String LOCK_KEY = "staged-feeding-ingredient-validator";

    @Autowired StagedFeedingIngredientValidator validator;
    @Autowired DistributedLockService lockService;
    @Autowired FeedIngredientProducer producer;
    @Autowired StagedFeedingIngredientRepository stagedRepo;
    @Autowired IngredientStockRepository stockRepo;
    @Autowired IngredientRepository ingredientRepo;
    @Autowired AuthRepository authRepository;
    @Autowired FarmRepository farmRepository;
    @Autowired AuthService authService;
    @Autowired ObjectMapper objectMapper;

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
        reg.setEmail("lock-feed@test.local");
        reg.setPassword("MasterPass1!");
        reg.setFarmName("Lock Feed Farm " + UUID.randomUUID());
        reg.setName("Lock Feed Master");
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
        userId = authRepository.findByEmail("lock-feed@test.local").orElseThrow().getId();
    }

    @Test
    void validator_skipsWhileLockHeld_byPeer_thenProcessesAfterRelease() throws Exception {
        UUID submissionId = stageIngredient();

        StagedFeedingIngredient before = stagedRepo.findByIdOnly(submissionId).orElseThrow();
        assertThat(before.getStatus()).isEqualTo(StagedStatus.PENDING);

        // Peer instance holds the lock for the duration of this block.
        lockService.runIfLocked(LOCK_KEY, Duration.ofSeconds(30), () -> {
            validator.processStaged();   // internally tries to acquire LOCK_KEY → skips
            return null;
        });

        // Row must still be PENDING — the validator short-circuited on the lock.
        StagedFeedingIngredient stillPending =
                stagedRepo.findByIdOnly(submissionId).orElseThrow();
        assertThat(stillPending.getStatus())
                .as("validator must skip because the lock is held by a peer")
                .isEqualTo(StagedStatus.PENDING);

        // Lock is released; a second call must promote the row.
        validator.processStaged();

        StagedFeedingIngredient after = stagedRepo.findByIdOnly(submissionId).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(StagedStatus.MOVED);
        assertThat(after.getMovedIngredientId()).isNotNull();
        assertThat(ingredientRepo.findAllByFarmIdLive(farmId)).hasSize(1);
    }

    private UUID stageIngredient() throws Exception {
        UUID submissionId = UUID.randomUUID();
        IngredientSubmissionPayload payload = new IngredientSubmissionPayload(
                "Lock Test Soya", IngredientUnit.KG, new BigDecimal("50"),
                new BigDecimal("1000"), Currency.XAF, "Lock Supplier", null);

        producer.publish(new FeedIngredientSubmissionEvent(
                submissionId, farmId, userId, "lock-feed@test.local",
                objectMapper.writeValueAsString(payload),
                LocalDateTime.now(), null, null));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(stagedRepo.findByIdOnly(submissionId)).isPresent());

        return submissionId;
    }
}