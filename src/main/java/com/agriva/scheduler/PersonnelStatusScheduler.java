package com.agriva.scheduler;

import com.agriva.model.entity.Personnel;
import com.agriva.model.entity.enums.PersonnelStatus;
import com.agriva.model.entity.PersonnelStatusHistory;
import com.agriva.repository.PersonnelRepository;
import com.agriva.repository.PersonnelStatusHistoryRepository;
import com.agriva.repository.SalaryPaymentRepository;
import com.agriva.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class PersonnelStatusScheduler {

    private final PersonnelRepository personnelRepository;
    private final PersonnelStatusHistoryRepository historyRepository;
    private final TaskRepository taskRepository;
    private final SalaryPaymentRepository salaryRepository;

    @Scheduled(cron = "0 * * * * *") // every minute
    @Transactional
    public void tick() {
        LocalDateTime now = LocalDateTime.now();
        reactivateExpired(now);
        purgeTerminated(now);
    }

    private void reactivateExpired(LocalDateTime now) {
        List<Personnel> expired = personnelRepository
                .findByStatusExpirationBeforeAndStatusIn(
                        now, List.of(PersonnelStatus.ON_LEAVE, PersonnelStatus.SUSPENDED));
        if (expired.isEmpty()) return;

        for (Personnel p : expired) {
            historyRepository.findByPersonnelIdAndEndTimestampIsNull(p.getId())
                    .ifPresent(h -> {
                        h.setEndTimestamp(now);
                        h.setActualDuration(Duration.between(h.getStartTimestamp(), now));
                        historyRepository.save(h);
                    });
            p.setStatus(PersonnelStatus.ACTIVE);
            p.setStatusExpiration(null);
            p.setSuspensionDuration(null);
            p.setStatusChangeTimestamp(now);
            personnelRepository.save(p);

            historyRepository.save(PersonnelStatusHistory.builder()
                    .personnel(p)
                    .status(PersonnelStatus.ACTIVE)
                    .startTimestamp(now)
                    .createdAt(now)
                    .build());
        }
        log.info("Auto-reactivated {} personnel", expired.size());
    }

    private void purgeTerminated(LocalDateTime now) {
        LocalDateTime cutoff = now.minusHours(72);
        personnelRepository
                .findByStatusAndTerminationTimestampBefore(PersonnelStatus.TERMINATED, cutoff)
                .forEach(p -> {
                    try {
                        taskRepository.deleteByPersonnelId(p.getId());
                        salaryRepository.deleteByPersonnelId(p.getId());
                        historyRepository.deleteByPersonnelId(p.getId());
                        personnelRepository.delete(p);
                        log.info("Purged terminated personnel {}", p.getId());
                    } catch (Exception e) {
                        log.error("Purge failed for personnel {}: {}", p.getId(), e.getMessage());
                    }
                });
    }
}