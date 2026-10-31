package com.agriva.personnel;

import com.agriva.TestcontainersConfiguration;
import com.agriva.model.dto.auth.AuthResponse;
import com.agriva.model.dto.auth.MasterRegisterRequest;
import com.agriva.model.dto.auth.SubUserRegisterRequest;
import com.agriva.model.dto.task.TaskCreateRequest;
import com.agriva.model.dto.task.TaskResponse;
import com.agriva.model.entity.*;
import com.agriva.model.entity.enums.Gender;
import com.agriva.model.entity.enums.PersonnelStatus;
import com.agriva.model.entity.enums.TaskStatus;
import com.agriva.repository.*;
import com.agriva.scheduler.PersonnelStatusScheduler;
import com.agriva.service.AuthService;
import com.agriva.service.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class PersonnelSchedulerIT {

    @Autowired PersonnelStatusScheduler statusScheduler;
    @Autowired TaskService taskService;
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
        reg.setEmail("schedmaster@test.local");
        reg.setPassword("MasterPass1");
        reg.setFarmName("Scheduler Farm " + UUID.randomUUID());
        reg.setName("Scheduler Master");
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
        masterUserId = authRepository.findByEmail("schedmaster@test.local").orElseThrow().getId();

        SubUserRegisterRequest sub = new SubUserRegisterRequest();
        sub.setEmail("schedworker@test.local");
        sub.setName("Scheduler Worker");
        sub.setUsername("schedworker");
        sub.setPassword("WorkerPass1");
        sub.setGender(Gender.FEMALE);
        sub.setDateOfBirth(LocalDate.now().minusYears(25));
        sub.setPhoneNumber("+237600000002");
        sub.setAddress("Rue 3");
        authService.registerSubUser(sub, farmId, "MASTER");

        UUID workerUserId = authRepository.findByEmail("schedworker@test.local")
                .orElseThrow().getId();
        personnelId = personnelRepository.findByUserId(workerUserId).orElseThrow().getId();
    }

    @Test
    void statusScheduler_reactivatesExpiredLeave() {
        Personnel p = personnelRepository.findById(personnelId).orElseThrow();
        p.setStatus(PersonnelStatus.ON_LEAVE);
        p.setStatusExpiration(LocalDateTime.now().minusMinutes(5));
        personnelRepository.save(p);

        statusScheduler.tick();

        Personnel reloaded = personnelRepository.findById(personnelId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(PersonnelStatus.ACTIVE);
        assertThat(reloaded.getStatusExpiration()).isNull();
    }

    @Test
    void statusScheduler_doesNotTouchUnExpired() {
        Personnel p = personnelRepository.findById(personnelId).orElseThrow();
        p.setStatus(PersonnelStatus.SUSPENDED);
        p.setStatusExpiration(LocalDateTime.now().plusDays(3));
        personnelRepository.save(p);

        statusScheduler.tick();

        Personnel reloaded = personnelRepository.findById(personnelId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(PersonnelStatus.SUSPENDED);
        assertThat(reloaded.getStatusExpiration()).isNotNull();
    }

    @Test
    void statusScheduler_purgesTerminatedOlderThan72Hours() {
        Personnel p = personnelRepository.findById(personnelId).orElseThrow();
        p.setStatus(PersonnelStatus.TERMINATED);
        p.setTerminationTimestamp(LocalDateTime.now().minusHours(73));
        personnelRepository.save(p);

        statusScheduler.tick();

        assertThat(personnelRepository.findById(personnelId)).isEmpty();
    }

    @Test
    void statusScheduler_keepsRecentlyTerminated() {
        Personnel p = personnelRepository.findById(personnelId).orElseThrow();
        p.setStatus(PersonnelStatus.TERMINATED);
        p.setTerminationTimestamp(LocalDateTime.now().minusHours(1));
        personnelRepository.save(p);

        statusScheduler.tick();

        assertThat(personnelRepository.findById(personnelId)).isPresent();
    }

    @Test
    void taskService_processExpiredTasks_marksUncompleted() {
        TaskResponse t = createTask(LocalDate.now().minusDays(2));
        assertThat(t.getStatus()).isEqualTo(TaskStatus.PENDING);

        taskService.processExpiredTasks();

        Task reloaded = taskRepository.findById(t.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(TaskStatus.UNCOMPLETED);
    }

    @Test
    void taskService_processUnvalidatedTasks_resetsSubmittedToUncompleted() {
        TaskResponse t = createTask(LocalDate.now().plusDays(3));
        taskService.action(t.getId(), farmId, masterUserId, true, "START");
        taskService.action(t.getId(), farmId, masterUserId, true, "SUBMIT");

        taskService.processUnvalidatedTasks();

        Task reloaded = taskRepository.findById(t.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(TaskStatus.UNCOMPLETED);
        assertThat(reloaded.getIsSubmitted()).isFalse();
        assertThat(reloaded.getIsValidated()).isFalse();
    }

    @Test
    void taskService_resetProductivityMetrics_zeroesCounters() {
        Personnel p = personnelRepository.findById(personnelId).orElseThrow();
        p.setTotalHoursWorkedLast30Days(42.0);
        p.setWorkingDaysCount(8);
        personnelRepository.save(p);

        taskService.resetProductivityMetrics();

        Personnel reloaded = personnelRepository.findById(personnelId).orElseThrow();
        assertThat(reloaded.getTotalHoursWorkedLast30Days()).isEqualTo(0.0);
        assertThat(reloaded.getWorkingDaysCount()).isEqualTo(0);
        assertThat(reloaded.getLastProductivityResetDate()).isEqualTo(LocalDate.now());
    }

    private TaskResponse createTask(LocalDate deadline) {
        TaskCreateRequest req = new TaskCreateRequest();
        req.setPersonnelId(personnelId);
        req.setTitle("Scheduled Task");
        req.setDeadline(deadline);
        req.setExpectedHours(2);
        return taskService.create(req, farmId, masterUserId);
    }
}