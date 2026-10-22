package com.agriva.personnel;

import com.agriva.TestcontainersConfiguration;
import com.agriva.exception.BusinessException;
import com.agriva.exception.InvalidPersonnelStatusException;
import com.agriva.model.dto.auth.AuthResponse;
import com.agriva.model.dto.auth.MasterRegisterRequest;
import com.agriva.model.dto.auth.SubUserRegisterRequest;
import com.agriva.model.dto.personnel.*;
import com.agriva.model.entity.*;
import com.agriva.model.entity.enums.Department;
import com.agriva.model.entity.enums.Gender;
import com.agriva.model.entity.enums.PersonnelStatus;
import com.agriva.repository.*;
import com.agriva.service.AuthService;
import com.agriva.service.PersonnelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class PersonnelStatusLifecycleIT {

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
    UUID personnelId;

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
        reg.setEmail("status@test.local");
        reg.setPassword("MasterPass1");
        reg.setFarmName("Status Farm " + UUID.randomUUID());
        reg.setName("Status Master");
        reg.setGender(Gender.MALE);
        reg.setDateOfBirth(LocalDate.now().minusYears(35));
        reg.setPhoneNumber("+237600000000");
        reg.setCountry("Cameroon"); reg.setState("Centre"); reg.setCity("Yaoundé");
        reg.setAddress("Rue 1"); reg.setPostalCode("00000");
        reg.setCurrency("XAF"); reg.setUserAgreement(true);
        AuthResponse master = authService.registerMaster(reg);
        farmId = master.getFarmId();

        SubUserRegisterRequest sub = new SubUserRegisterRequest();
        sub.setEmail("worker@test.local");
        sub.setName("Worker");
        sub.setUsername("worker");
        sub.setPassword("WorkerPass1");
        sub.setFarmId(farmId);
        sub.setGender(Gender.FEMALE);
        sub.setDateOfBirth(LocalDate.now().minusYears(25));
        sub.setPhoneNumber("+237600000002");
        sub.setAddress("Rue 3");
        authService.registerSubUser(sub);

        UUID userId = authRepository.findByEmail("worker@test.local").orElseThrow().getId();
        personnelId = personnelRepository.findByUserId(userId).orElseThrow().getId();
    }

    @Test
    void changeToOnLeave_setsExpirationAndClosesCurrentHistory() {
        PersonnelStatusUpdateRequest req = new PersonnelStatusUpdateRequest();
        req.setStatus(PersonnelStatus.ON_LEAVE);
        req.setLeaveStartDate(LocalDate.now());
        req.setExpectedReturnDate(LocalDate.now().plusDays(5));

        PersonnelResponse res = personnelService.changeStatus(personnelId, req, farmId);

        assertThat(res.getStatus()).isEqualTo(PersonnelStatus.ON_LEAVE);
        assertThat(res.getStatusExpiration()).isNotNull();
        assertThat(res.getStatusExpiration().toLocalDate())
                .isEqualTo(LocalDate.now().plusDays(5));

        List<PersonnelStatusHistoryResponse> history =
                personnelService.getStatusHistory(personnelId, farmId);
        assertThat(history).hasSize(2);
        // newest first
        assertThat(history.get(0).getStatus()).isEqualTo(PersonnelStatus.ON_LEAVE);
        assertThat(history.get(0).getEndTimestamp()).isNull();
        // old one was closed
        assertThat(history.get(1).getStatus()).isEqualTo(PersonnelStatus.ACTIVE);
        assertThat(history.get(1).getEndTimestamp()).isNotNull();
        assertThat(history.get(1).getActualDuration()).isNotNull();
    }

    @Test
    void changeToSuspended_requiresPositiveDuration() {
        PersonnelStatusUpdateRequest req = new PersonnelStatusUpdateRequest();
        req.setStatus(PersonnelStatus.SUSPENDED);
        req.setSuspensionDuration(Duration.ofHours(48));

        PersonnelResponse res = personnelService.changeStatus(personnelId, req, farmId);
        assertThat(res.getStatus()).isEqualTo(PersonnelStatus.SUSPENDED);
        assertThat(res.getSuspensionDuration()).isEqualTo(Duration.ofHours(48));
        assertThat(res.getStatusExpiration()).isNotNull();
    }

    @Test
    void changeToSuspended_rejectsOver30Days() {
        PersonnelStatusUpdateRequest req = new PersonnelStatusUpdateRequest();
        req.setStatus(PersonnelStatus.SUSPENDED);
        req.setSuspensionDuration(Duration.ofDays(31));

        assertThatThrownBy(() -> personnelService.changeStatus(personnelId, req, farmId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("30");
    }

    @Test
    void changeToTerminated_setsTerminationTimestamp() {
        PersonnelStatusUpdateRequest req = new PersonnelStatusUpdateRequest();
        req.setStatus(PersonnelStatus.TERMINATED);

        PersonnelResponse res = personnelService.changeStatus(personnelId, req, farmId);
        assertThat(res.getStatus()).isEqualTo(PersonnelStatus.TERMINATED);
        assertThat(res.getTerminationTimestamp()).isNotNull();
        assertThat(res.getStatusExpiration()).isNull();
    }

    @Test
    void terminatedPersonnel_cannotTransitionAgain() {
        PersonnelStatusUpdateRequest term = new PersonnelStatusUpdateRequest();
        term.setStatus(PersonnelStatus.TERMINATED);
        personnelService.changeStatus(personnelId, term, farmId);

        PersonnelStatusUpdateRequest back = new PersonnelStatusUpdateRequest();
        back.setStatus(PersonnelStatus.ACTIVE);
        assertThatThrownBy(() -> personnelService.changeStatus(personnelId, back, farmId))
                .isInstanceOf(InvalidPersonnelStatusException.class);
    }

    @Test
    void farmManagerCannotTakeLeave() {
        // promote by setting department directly
        Personnel p = personnelRepository.findById(personnelId).orElseThrow();
        p.setDepartment(Department.FARM_MANAGEMENT);
        personnelRepository.save(p);

        PersonnelStatusUpdateRequest req = new PersonnelStatusUpdateRequest();
        req.setStatus(PersonnelStatus.ON_LEAVE);
        req.setLeaveStartDate(LocalDate.now());
        req.setExpectedReturnDate(LocalDate.now().plusDays(3));

        assertThatThrownBy(() -> personnelService.changeStatus(personnelId, req, farmId))
                .isInstanceOf(InvalidPersonnelStatusException.class)
                .hasMessageContaining("Farm managers");
    }

    @Test
    void onLeaveWithoutDates_rejected() {
        PersonnelStatusUpdateRequest req = new PersonnelStatusUpdateRequest();
        req.setStatus(PersonnelStatus.ON_LEAVE);
        // both dates missing

        assertThatThrownBy(() -> personnelService.changeStatus(personnelId, req, farmId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Leave");
    }
}