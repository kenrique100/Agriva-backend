package com.agriva.personnel;

import com.agriva.TestcontainersConfiguration;
import com.agriva.exception.BusinessException;
import com.agriva.model.dto.auth.AuthResponse;
import com.agriva.model.dto.auth.MasterRegisterRequest;
import com.agriva.model.dto.auth.SubUserRegisterRequest;
import com.agriva.model.dto.personnel.PersonnelResponse;
import com.agriva.model.dto.personnel.PersonnelUpdateRequest;
import com.agriva.model.entity.*;
import com.agriva.repository.*;
import com.agriva.service.AuthService;
import com.agriva.service.PersonnelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class PersonnelLifecycleIT {

    @Autowired PersonnelService personnelService;
    @Autowired AuthService authService;
    @Autowired PersonnelRepository personnelRepository;
    @Autowired PersonnelStatusHistoryRepository historyRepository;
    @Autowired TaskRepository taskRepository;
    @Autowired SalaryPaymentRepository salaryRepository;
    @Autowired ExpenseRepository expenseRepository;
    @Autowired InvestmentRepository investmentRepository;
    @Autowired IncomeRepository incomeRepository;
    @Autowired TransactionRepository transactionRepository;
    @Autowired AuthRepository authRepository;
    @Autowired FarmRepository farmRepository;

    UUID farmId;
    UUID masterUserId;
    UUID masterPersonnelId;

    @BeforeEach
    void setUp() {
        salaryRepository.deleteAll();
        taskRepository.deleteAll();
        historyRepository.deleteAll();
        personnelRepository.deleteAll();
        expenseRepository.deleteAll();
        investmentRepository.deleteAll();
        incomeRepository.deleteAll();
        transactionRepository.deleteAll();
        authRepository.deleteAll();
        farmRepository.deleteAll();

        MasterRegisterRequest reg = new MasterRegisterRequest();
        reg.setEmail("master@test.local");
        reg.setUsername("master_test");
        reg.setPassword("MasterPass1");
        reg.setFarmName("Lifecycle Farm " + UUID.randomUUID());
        reg.setName("Master Owner");
        reg.setGender(Gender.MALE);
        reg.setDateOfBirth(LocalDate.now().minusYears(35));
        reg.setPhoneNumber("+237600000000");
        reg.setCountry("Cameroon"); reg.setState("Centre"); reg.setCity("Yaoundé");
        reg.setAddress("Rue 1"); reg.setPostalCode("00000");
        reg.setCurrency("XAF"); reg.setUserAgreement(true);

        AuthResponse master = authService.registerMaster(reg);
        farmId = master.getFarmId();
        masterUserId = authRepository.findByEmail("master@test.local").orElseThrow().getId();

        // The registration should have auto-created a Personnel row.
        Personnel masterPersonnel = personnelRepository.findByUserId(masterUserId)
                .orElseThrow(() -> new AssertionError("Personnel not auto-created for MASTER"));
        masterPersonnelId = masterPersonnel.getId();
    }

    @Test
    void masterRegistration_autoCreatesLinkedPersonnel() {
        Personnel p = personnelRepository.findByUserId(masterUserId).orElseThrow();
        assertThat(p.getFarmId()).isEqualTo(farmId);
        assertThat(p.getStatus()).isEqualTo(PersonnelStatus.ACTIVE);
        assertThat(p.getName()).isEqualTo("Master Owner");
        assertThat(p.getGender()).isEqualTo(Gender.MALE);
        assertThat(p.getDepartment()).isEqualTo(Department.ADMINISTRATION);
        assertThat(p.getDateOfEmployment()).isEqualTo(LocalDate.now());
    }

    @Test
    void subUserRegistration_autoCreatesLinkedPersonnel() {
        UUID subUserId = registerSubUser("john@test.local", "John Doe");

        Personnel p = personnelRepository.findByUserId(subUserId).orElseThrow();
        assertThat(p.getFarmId()).isEqualTo(farmId);
        assertThat(p.getName()).isEqualTo("John Doe");
        assertThat(p.getStatus()).isEqualTo(PersonnelStatus.ACTIVE);
        assertThat(personnelRepository.count()).isEqualTo(2); // master + sub
    }

    @Test
    void masterAndSubUser_bothAppearInFarmPersonnelList() {
        registerSubUser("sub1@test.local", "Sub One");
        registerSubUser("sub2@test.local", "Sub Two");

        var page = personnelService.list(farmId, null,
                org.springframework.data.domain.PageRequest.of(0, 20));
        assertThat(page.getTotalElements()).isEqualTo(3); // master + 2 subs
    }

    @Test
    void personnelIs1to1WithUser() {
        UUID subUserId = registerSubUser("one@test.local", "Only One");
        assertThat(personnelRepository.findByUserId(subUserId)).isPresent();
        assertThat(personnelRepository.findAll().stream()
                .filter(p -> p.getUserId().equals(subUserId))
                .count()).isEqualTo(1);
    }


    @Test
    void updatePersonnel_fillsHrFieldsThatWereNullOnRegistration() {
        UUID subUserId = registerSubUser("emp@test.local", "Employee");
        Personnel p = personnelRepository.findByUserId(subUserId).orElseThrow();
        assertThat(p.getNationalId()).isNull();

        PersonnelUpdateRequest req = new PersonnelUpdateRequest();
        req.setNationalId("123456789");
        req.setDepartment(Department.CROPS);
        req.setDateOfEmployment(LocalDate.now().minusMonths(2));

        PersonnelResponse updated = personnelService.update(p.getId(), req, farmId);

        assertThat(updated.getNationalId()).isEqualTo("123456789");
        assertThat(updated.getDepartment()).isEqualTo(Department.CROPS);
    }

    @Test
    void duplicateNationalId_rejected() {
        UUID a = registerSubUser("a@test.local", "A");
        UUID b = registerSubUser("b@test.local", "B");
        Personnel pA = personnelRepository.findByUserId(a).orElseThrow();
        Personnel pB = personnelRepository.findByUserId(b).orElseThrow();

        PersonnelUpdateRequest req1 = new PersonnelUpdateRequest();
        req1.setNationalId("111222333");
        personnelService.update(pA.getId(), req1, farmId);

        PersonnelUpdateRequest req2 = new PersonnelUpdateRequest();
        req2.setNationalId("111222333");

        assertThatThrownBy(() -> personnelService.update(pB.getId(), req2, farmId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("National ID");
    }


    @Test
    void subUser_canReadOwnProfile() {
        UUID subUserId = registerSubUser("own@test.local", "Self Service");
        var profile = personnelService.getOwnProfile(subUserId);
        assertThat(profile.getPersonnel().getUserId()).isEqualTo(subUserId);
        assertThat(profile.getPersonnel().getName()).isEqualTo("Self Service");
    }

    @Test
    void deletePersonnel_deletesUserViaCascade() {
        UUID subUserId = registerSubUser("gone@test.local", "Gone Soon");
        Personnel p = personnelRepository.findByUserId(subUserId).orElseThrow();

        personnelService.delete(p.getId(), farmId);

        assertThat(personnelRepository.findById(p.getId())).isEmpty();
        assertThat(authRepository.findById(subUserId)).isEmpty();
    }

    @Test
    void deleteMaster_viaPersonnelEndpoint_rejected() {
        assertThatThrownBy(() -> personnelService.delete(masterPersonnelId, farmId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("MASTER");
    }


    private UUID registerSubUser(String email, String name) {
        SubUserRegisterRequest req = new SubUserRegisterRequest();
        req.setEmail(email);
        req.setName(name);
        req.setUsername(email.split("@")[0]);
        req.setPassword("SubPass123");
        req.setFarmId(farmId);
        req.setGender(Gender.FEMALE);
        req.setDateOfBirth(LocalDate.now().minusYears(25));
        req.setPhoneNumber("+237600000001");
        req.setAddress("Rue 2");
        authService.registerSubUser(req);
        return authRepository.findByEmail(email).orElseThrow().getId();
    }
}