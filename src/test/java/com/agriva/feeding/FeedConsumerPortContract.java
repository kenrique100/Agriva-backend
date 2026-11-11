package com.agriva.feeding;

import com.agriva.TestcontainersConfiguration;
import com.agriva.farming.repository.FarmRepository;
import com.agriva.feeding.port.ConsumerSnapshot;
import com.agriva.feeding.port.FeedConsumerPort;
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
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Abstract contract that every {@link FeedConsumerPort} implementation must
 * pass. A concrete subclass supplies three things:
 *
 * <ol>
 *   <li>{@link #port()} — the adapter bean under test</li>
 *   <li>{@link #createLiveConsumer(UUID, UUID)} — creates a domain entity that
 *       this adapter can snapshoot, returns a fixture</li>
 *   <li>{@link #softDeleteConsumer(UUID, UUID)} — makes the previously-created
 *       consumer invisible to the adapter</li>
 * </ol>
 *
 * The base class bootstraps a MASTER user + farm for each test, so subclasses
 * do not need to repeat that boilerplate.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public abstract class FeedConsumerPortContract {

    @Autowired protected AuthService authService;
    @Autowired protected AuthRepository authRepository;
    @Autowired protected FarmRepository farmRepository;

    protected UUID farmId;
    protected UUID userId;

    protected abstract FeedConsumerPort port();

    protected abstract ConsumerFixture createLiveConsumer(UUID farmId, UUID userId);

    protected abstract void softDeleteConsumer(UUID consumerId, UUID farmId);

    /**
     * Public so concrete subclasses in other packages can construct fixtures.
     * (A {@code protected} record's canonical constructor is {@code protected},
     * which blocks instantiation from a subclass whose type is not itself a
     * subtype of this record.)
     */
    public record ConsumerFixture(UUID consumerId, String displayName, long expectedHeadcount) {}

    @BeforeEach
    void bootstrapFarm() {
        authRepository.deleteAll();
        farmRepository.deleteAll();

        MasterRegisterRequest reg = new MasterRegisterRequest();
        reg.setEmail("contract-" + UUID.randomUUID() + "@test.local");
        reg.setPassword("MasterPass1!");
        reg.setFarmName("Contract Farm " + UUID.randomUUID());
        reg.setName("Contract Master");
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
    void consumerType_isNotNull() {
        assertThat(port().consumerType()).isNotNull();
    }

    @Test
    void snapshot_liveConsumer_returnsPresent() {
        ConsumerFixture f = createLiveConsumer(farmId, userId);
        Optional<ConsumerSnapshot> snap = port().snapshot(farmId, f.consumerId());
        assertThat(snap).isPresent();
        assertThat(snap.get().consumerId()).isEqualTo(f.consumerId());
        assertThat(snap.get().headcount()).isEqualTo(f.expectedHeadcount());
        assertThat(snap.get().displayName()).isNotBlank();
    }

    @Test
    void snapshot_deletedConsumer_returnsEmpty() {
        ConsumerFixture f = createLiveConsumer(farmId, userId);
        softDeleteConsumer(f.consumerId(), farmId);
        assertThat(port().snapshot(farmId, f.consumerId())).isEmpty();
    }

    @Test
    void snapshot_unknownFarm_returnsEmpty() {
        ConsumerFixture f = createLiveConsumer(farmId, userId);
        assertThat(port().snapshot(UUID.randomUUID(), f.consumerId())).isEmpty();
    }

    @Test
    void snapshot_unknownConsumerId_returnsEmpty() {
        assertThat(port().snapshot(farmId, UUID.randomUUID())).isEmpty();
    }

    @Test
    void resolveByDisplayName_roundTrips() {
        ConsumerFixture f = createLiveConsumer(farmId, userId);
        Optional<UUID> resolved = port().resolveConsumerIdByDisplayName(farmId, f.displayName());
        assertThat(resolved).contains(f.consumerId());
    }

    @Test
    void resolveByDisplayName_unknown_returnsEmpty() {
        assertThat(port().resolveConsumerIdByDisplayName(farmId, "no-such-consumer-xyz"))
                .isEmpty();
    }
}