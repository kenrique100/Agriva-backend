package com.agriva.personnel;

import com.agriva.TestcontainersConfiguration;
import com.agriva.farming.repository.FarmRepository;
import com.agriva.finance.repository.ExpenseRepository;
import com.agriva.finance.repository.IncomeRepository;
import com.agriva.finance.repository.InvestmentRepository;
import com.agriva.finance.repository.TransactionRepository;
import com.agriva.identity.repository.AuthRepository;
import com.agriva.payroll.repository.SalaryPaymentRepository;
import com.agriva.personnel.models.entity.Personnel;
import com.agriva.personnel.repository.PersonnelRepository;
import com.agriva.personnel.repository.PersonnelStatusHistoryRepository;
import com.agriva.shared.exception.InvalidTaskStateException;
import com.agriva.shared.exception.PersonnelAccessDeniedException;
import com.agriva.identity.models.dto.response.AuthResponse;
import com.agriva.identity.models.dto.request.MasterRegisterRequest;
import com.agriva.identity.models.dto.request.SubUserRegisterRequest;
import com.agriva.personnel.models.enums.Gender;
import com.agriva.task.model.dto.requests.TaskCreateRequest;
import com.agriva.task.model.dto.response.TaskResponse;
import com.agriva.task.model.enums.TaskStatus;
import com.agriva.identity.service.AuthService;
import com.agriva.task.service.TaskService;
import com.agriva.task.model.entity.Task;
import com.agriva.task.repository.TaskRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class TaskLifecycleIT {

    @Autowired TaskService taskService;
    @Autowired AuthService authService;
    @Autowired
    PersonnelRepository personnelRepository;
    @Autowired
    PersonnelStatusHistoryRepository historyRepository;
    @Autowired
    TaskRepository taskRepository;
    @Autowired
    SalaryPaymentRepository salaryRepository;
    @Autowired
    ExpenseRepository expenseRepository;
    @Autowired
    InvestmentRepository investmentRepository;
    @Autowired
    IncomeRepository incomeRepository;
    @Autowired
    TransactionRepository transactionRepository;
    @Autowired
    AuthRepository authRepository;
    @Autowired
    FarmRepository farmRepository;

    UUID farmId;
    UUID masterUserId;
    UUID personnelId;
    UUID workerUserId;

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
        reg.setEmail("taskmaster@test.local");
        reg.setPassword("MasterPass1!");
        reg.setFarmName("Task Farm " + UUID.randomUUID());
        reg.setName("Task Master");
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
        masterUserId = authRepository.findByEmail("taskmaster@test.local").orElseThrow().getId();

        SubUserRegisterRequest sub = new SubUserRegisterRequest();
        sub.setEmail("worker@test.local");
        sub.setName("Task Worker");
        sub.setUsername("taskworker");
        sub.setPassword("WorkerPass1!");
        sub.setGender(Gender.FEMALE);
        sub.setDateOfBirth(LocalDate.now().minusYears(25));
        sub.setPhoneNumber("+237600000002");
        sub.setAddress("Rue 3");
        authService.registerSubUser(sub, farmId, "MASTER");

        workerUserId = authRepository.findByEmail("worker@test.local")
                .orElseThrow().getId();
        personnelId = personnelRepository.findByUserId(workerUserId).orElseThrow().getId();
    }

    @Test
    void fullLifecycle_start_stop_continue_submit_validate() {
        TaskResponse t = createTask();

        assertThat(t.getStatus()).isEqualTo(TaskStatus.PENDING);
        assertThat(t.getPersonnelId()).isEqualTo(personnelId);

        TaskResponse started = taskService.action(t.getId(), farmId, workerUserId, false, "START");
        assertThat(started.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(started.getStartTime()).isNotNull();
        assertThat(started.getLastResumeTime()).isNotNull();

        Task raw = taskRepository.findById(t.getId()).orElseThrow();
        raw.setLastResumeTime(LocalDateTime.now().minusHours(2));
        taskRepository.save(raw);

        TaskResponse stopped = taskService.action(t.getId(), farmId, workerUserId, false, "STOP");
        assertThat(stopped.getStatus()).isEqualTo(TaskStatus.STOPPED);
        assertThat(stopped.getActualHours()).isBetween(1.9, 2.1);
        assertThat(stopped.getTotalWorkedMinutes()).isBetween(114L, 126L);

        TaskResponse continued = taskService.action(t.getId(), farmId, workerUserId, false, "CONTINUE");
        assertThat(continued.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);

        Task raw2 = taskRepository.findById(t.getId()).orElseThrow();
        raw2.setLastResumeTime(LocalDateTime.now().minusHours(1));
        taskRepository.save(raw2);

        TaskResponse submitted = taskService.action(t.getId(), farmId, workerUserId, false, "SUBMIT");
        assertThat(submitted.getStatus()).isEqualTo(TaskStatus.SUBMITTED);
        assertThat(submitted.getIsSubmitted()).isTrue();
        assertThat(submitted.getActualHours()).isBetween(2.9, 3.1);

        TaskResponse validated = taskService.validate(t.getId(), farmId, masterUserId, true);
        assertThat(validated.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(validated.getIsValidated()).isTrue();
        assertThat(validated.getIsSubmitted()).isFalse();
        assertThat(validated.getValidatedByUserId()).isEqualTo(masterUserId);

        Personnel p = personnelRepository.findById(personnelId).orElseThrow();
        assertThat(p.getTotalHoursWorkedLast30Days()).isGreaterThan(2.5);
        assertThat(p.getWorkingDaysCount()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void validateRejects_marksCancelled() {
        TaskResponse t = createTask();
        taskService.action(t.getId(), farmId, workerUserId, false, "START");
        taskService.action(t.getId(), farmId, workerUserId, false, "SUBMIT");

        TaskResponse rejected = taskService.validate(t.getId(), farmId, masterUserId, false);
        assertThat(rejected.getStatus()).isEqualTo(TaskStatus.CANCELLED);
        assertThat(rejected.getIsValidated()).isFalse();
    }

    @Test
    void validateOnlySubmittedTasks() {
        TaskResponse t = createTask();
        assertThatThrownBy(() -> taskService.validate(t.getId(), farmId, masterUserId, true))
                .isInstanceOf(InvalidTaskStateException.class)
                .hasMessageContaining("submitted");
    }

    @Test
    void startOnlyFromPending() {
        TaskResponse t = createTask();
        taskService.action(t.getId(), farmId, workerUserId, false, "START");
        assertThatThrownBy(() ->
                taskService.action(t.getId(), farmId, workerUserId, false, "START"))
                .isInstanceOf(InvalidTaskStateException.class)
                .hasMessageContaining("PENDING");
    }

    @Test
    void subUser_cannotActOnOthersTask() {
        TaskResponse t = createTask();

        SubUserRegisterRequest other = new SubUserRegisterRequest();
        other.setEmail("other@test.local");
        other.setName("Other");
        other.setUsername("other");
        other.setPassword("OtherPass12!");
        other.setGender(Gender.MALE);
        other.setDateOfBirth(LocalDate.now().minusYears(28));
        other.setPhoneNumber("+237600000005");
        other.setAddress("Rue 5");
        authService.registerSubUser(other, farmId, "MASTER");

        UUID otherUserId = authRepository.findByEmail("other@test.local")
                .orElseThrow().getId();

        assertThatThrownBy(() ->
                taskService.action(t.getId(), farmId, otherUserId, false, "START"))
                .isInstanceOf(PersonnelAccessDeniedException.class);
    }

    @Test
    void master_canActOnAnyFarmTask() {
        TaskResponse t = createTask();
        TaskResponse started = taskService.action(t.getId(), farmId, masterUserId, true, "START");
        assertThat(started.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
    }

    private TaskResponse createTask() {
        TaskCreateRequest req = new TaskCreateRequest();
        req.setPersonnelId(personnelId);
        req.setTitle("Harvest Plot A");
        req.setDescription("Bring the harvester");
        req.setDeadline(LocalDate.now().plusDays(7));
        req.setExpectedHours(4);
        return taskService.create(req, farmId, masterUserId);
    }
}