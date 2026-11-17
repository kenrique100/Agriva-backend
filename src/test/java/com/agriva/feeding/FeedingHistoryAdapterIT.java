package com.agriva.feeding;

import com.agriva.TestcontainersConfiguration;
import com.agriva.farming.repository.FarmRepository;
import com.agriva.feeding.port.ConsumerType;
import com.agriva.feeding.port.FeedingHistoryPort;
import com.agriva.feeding.repository.FeedingEventRepository;
import com.agriva.identity.models.dto.request.MasterRegisterRequest;
import com.agriva.identity.models.dto.response.AuthResponse;
import com.agriva.identity.repository.AuthRepository;
import com.agriva.identity.service.AuthService;
import com.agriva.personnel.models.enums.Gender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises {@link FeedingHistoryPort} directly. Rows are inserted into
 * {@code feeding_event} via JDBC rather than through {@code FeedConsumptionService.feed},
 * because the write path validates the consumer via the port SPI (a random
 * UUID consumer would be rejected), and this test's scope is the read-side
 * aggregation, not the write path.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class FeedingHistoryAdapterIT {

    @Autowired FeedingHistoryPort historyPort;
    @Autowired FeedingEventRepository feedingEventRepo;
    @Autowired AuthService authService;
    @Autowired AuthRepository authRepository;
    @Autowired FarmRepository farmRepository;
    @Autowired JdbcTemplate jdbc;

    UUID farmId;
    UUID userId;

    @BeforeEach
    void setUp() {
        feedingEventRepo.deleteAll();
        authRepository.deleteAll();
        farmRepository.deleteAll();

        MasterRegisterRequest reg = new MasterRegisterRequest();
        reg.setEmail("fhist-" + UUID.randomUUID() + "@test.local");
        reg.setPassword("MasterPass1!");
        reg.setFarmName("FHist Farm " + UUID.randomUUID());
        reg.setName("FHist Master");
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
        userId = authRepository.findByEmail(reg.getEmail()).orElseThrow().getId();
    }

    @Test
    void totalFeedConsumed_aggregatesWithinRange() {
        UUID consumer = UUID.randomUUID();
        deliver(consumer, "10", LocalDate.now().minusDays(5));
        deliver(consumer, "15", LocalDate.now().minusDays(2));
        deliver(consumer, "20", LocalDate.now());
        deliver(consumer, "99", LocalDate.now().minusDays(30)); // outside range

        BigDecimal sum = historyPort.totalFeedConsumedKg(
                farmId, ConsumerType.POULTRY, consumer,
                LocalDate.now().minusDays(7), LocalDate.now());

        assertThat(sum).isEqualByComparingTo("45");
    }

    @Test
    void totalFeedConsumed_noHistory_returnsZero() {
        assertThat(historyPort.totalFeedConsumedKg(
                farmId, ConsumerType.POULTRY, UUID.randomUUID(),
                LocalDate.now().minusDays(1), LocalDate.now()))
                .isEqualByComparingTo("0");
    }

    @Test
    void totalFeedConsumed_differentConsumer_isolated() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        deliver(a, "10", LocalDate.now());
        deliver(b, "40", LocalDate.now());

        assertThat(historyPort.totalFeedConsumedKg(
                farmId, ConsumerType.POULTRY, a,
                LocalDate.now().minusDays(1), LocalDate.now()))
                .isEqualByComparingTo("10");
        assertThat(historyPort.totalFeedConsumedKg(
                farmId, ConsumerType.POULTRY, b,
                LocalDate.now().minusDays(1), LocalDate.now()))
                .isEqualByComparingTo("40");
    }

    @Test
    void dailyFeedConsumed_returnsPerDayAggregate() {
        UUID consumer = UUID.randomUUID();
        LocalDate d1 = LocalDate.now().minusDays(3);
        LocalDate d2 = LocalDate.now().minusDays(1);
        deliver(consumer, "10", d1);
        deliver(consumer, "5",  d1);
        deliver(consumer, "8",  d2);

        var daily = historyPort.dailyFeedConsumed(
                farmId, ConsumerType.POULTRY, consumer,
                LocalDate.now().minusDays(5), LocalDate.now());

        assertThat(daily).hasSize(2);
        assertThat(daily.get(0).date()).isEqualTo(d1);
        assertThat(daily.get(0).quantityKg()).isEqualByComparingTo("15");
        assertThat(daily.get(0).feedingCount()).isEqualTo(2);
        assertThat(daily.get(1).date()).isEqualTo(d2);
        assertThat(daily.get(1).quantityKg()).isEqualByComparingTo("8");
        assertThat(daily.get(1).feedingCount()).isEqualTo(1);
    }

    @Test
    void dailyFeedConsumed_noHistory_returnsEmptyList() {
        assertThat(historyPort.dailyFeedConsumed(
                farmId, ConsumerType.POULTRY, UUID.randomUUID(),
                LocalDate.now().minusDays(1), LocalDate.now()))
                .isEmpty();
    }

    /**
     * Inserts a {@code feeding_event} row directly. Bypasses {@code
     * FeedConsumptionService.feed}, which would reject a random-UUID consumer
     * via the {@link com.agriva.feeding.port.FeedConsumerPort} SPI. The read
     * path being tested only cares that the row exists with the right
     * (farmId, consumerType, consumerId, quantityKg, fedAt).
     */
    private void deliver(UUID consumer, String kg, LocalDate date) {
        jdbc.update(
                "INSERT INTO feeding_event " +
                        "  (id, farm_id, consumer_type, consumer_id, consumer_display_name, " +
                        "   consumer_headcount, quantity_kg, fed_at, created_at) " +
                        "VALUES (?, ?, 'POULTRY', ?, 'test', 0, ?, ?, NOW())",
                UUID.randomUUID(), farmId, consumer, new BigDecimal(kg), date);
    }
}