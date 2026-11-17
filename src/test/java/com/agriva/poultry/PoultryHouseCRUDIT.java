package com.agriva.poultry;

import com.agriva.TestcontainersConfiguration;
import com.agriva.farming.repository.FarmRepository;
import com.agriva.identity.models.dto.request.MasterRegisterRequest;
import com.agriva.identity.models.dto.response.AuthResponse;
import com.agriva.identity.repository.AuthRepository;
import com.agriva.identity.service.AuthService;
import com.agriva.personnel.models.enums.Gender;
import com.agriva.poultry.model.dto.request.PoultryHouseRequest;
import com.agriva.poultry.model.dto.response.PoultryHouseResponse;
import com.agriva.poultry.repository.PoultryBatchRepository;
import com.agriva.poultry.repository.PoultryHouseRepository;
import com.agriva.poultry.service.PoultryHouseService;
import com.agriva.shared.exception.BusinessException;
import com.agriva.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class PoultryHouseCRUDIT {

    @Autowired PoultryHouseService houseService;
    @Autowired AuthService authService;
    @Autowired PoultryHouseRepository houseRepo;
    @Autowired PoultryBatchRepository batchRepo;
    @Autowired AuthRepository authRepository;
    @Autowired FarmRepository farmRepository;

    UUID farmId;
    UUID userId;

    @BeforeEach
    void setUp() {
        batchRepo.deleteAll();
        houseRepo.deleteAll();
        authRepository.deleteAll();
        farmRepository.deleteAll();

        MasterRegisterRequest reg = new MasterRegisterRequest();
        reg.setEmail("phouse@test.local");
        reg.setPassword("MasterPass1!");
        reg.setFarmName("PHouse Farm " + UUID.randomUUID());
        reg.setName("PHouse Master");
        reg.setGender(Gender.MALE);
        reg.setDateOfBirth(java.time.LocalDate.now().minusYears(35));
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
        userId = authRepository.findByEmail("phouse@test.local").orElseThrow().getId();
    }

    @Test
    void createHouse_persistsAndReturns() {
        PoultryHouseResponse created = houseService.createHouse(houseReq("House A"), farmId, userId);
        assertThat(created.getId()).isNotNull();
        assertThat(created.getName()).isEqualTo("House A");
        assertThat(created.getFarmId()).isEqualTo(farmId);
        assertThat(created.isActive()).isTrue();
        assertThat(created.getBatches()).isNull();
    }

    @Test
    void listHouses_returnsOnlyLiveHouses() {
        houseService.createHouse(houseReq("A"), farmId, userId);
        houseService.createHouse(houseReq("B"), farmId, userId);
        houseService.createHouse(houseReq("C"), farmId, userId);

        var deleted = houseService.createHouse(houseReq("D"), farmId, userId);
        houseService.deleteHouse(deleted.getId(), farmId);

        var page = houseService.listHouses(farmId, PageRequest.of(0, 20));
        assertThat(page.getTotalElements()).isEqualTo(3);
    }

    @Test
    void duplicateHouseName_rejected() {
        houseService.createHouse(houseReq("North House"), farmId, userId);
        assertThatThrownBy(() ->
                houseService.createHouse(houseReq("North House"), farmId, userId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void duplicateHouseName_caseInsensitive_rejected() {
        houseService.createHouse(houseReq("North House"), farmId, userId);
        assertThatThrownBy(() ->
                houseService.createHouse(houseReq("NORTH HOUSE"), farmId, userId))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void updateHouse_renames() {
        var house = houseService.createHouse(houseReq("Old Name"), farmId, userId);
        var updated = houseService.updateHouse(house.getId(), houseReq("New Name"), farmId);
        assertThat(updated.getName()).isEqualTo("New Name");
    }

    @Test
    void updateHouse_duplicateName_rejected() {
        houseService.createHouse(houseReq("A"), farmId, userId);
        var b = houseService.createHouse(houseReq("B"), farmId, userId);
        assertThatThrownBy(() -> houseService.updateHouse(b.getId(), houseReq("A"), farmId))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void deleteHouse_softDeletes() {
        var house = houseService.createHouse(houseReq("North"), farmId, userId);
        houseService.deleteHouse(house.getId(), farmId);
        assertThatThrownBy(() -> houseService.getHouse(house.getId(), farmId))
                .isInstanceOf(ResourceNotFoundException.class);
        var deleted = houseRepo.findById(house.getId()).orElseThrow();
        assertThat(deleted.getDeletedAt()).isNotNull();
    }

    @Test
    void getHouse_unknownId_throws() {
        assertThatThrownBy(() -> houseService.getHouse(UUID.randomUUID(), farmId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getHouse_otherFarm_throws() {
        var house = houseService.createHouse(houseReq("Mine"), farmId, userId);
        assertThatThrownBy(() -> houseService.getHouse(house.getId(), UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private PoultryHouseRequest houseReq(String name) {
        PoultryHouseRequest r = new PoultryHouseRequest();
        r.setName(name);
        r.setLocation("Sector A");
        r.setCapacity(5000);
        return r;
    }
}