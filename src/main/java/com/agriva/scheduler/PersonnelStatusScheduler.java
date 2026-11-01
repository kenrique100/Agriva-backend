package com.agriva.scheduler;

import com.agriva.model.entity.Personnel;
import com.agriva.model.entity.enums.PersonnelStatus;
import com.agriva.model.entity.PersonnelStatusHistory;
import com.agriva.repository.PersonnelRepository;
import com.agriva.repository.PersonnelStatusHistoryRepository;
import com.agriva.repository.SalaryPaymentRepository;
import com.agriva.repository.TaskRepository;
import com.agriva.service.DistributedLockService;
import com.agriva.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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
    private final DistributedLockService lock;
    private final FileStorageService fileStorageService;

    @Scheduled(cron = "0 * * * * *")
    public void tick() {
        lock.runIfLocked("personnel-status-tick", Duration.ofSeconds(55), () -> {
            LocalDateTime now = LocalDateTime.now();
            reactivateExpired(now);
            purgeTerminated(now);
            return null;
        });
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

    /**
     * Hard-deletes personnel terminated >72h ago.
     * Also deletes their profile picture files — otherwise the uploads volume
     * leaks one PNG/JPG per terminated employee forever (audit C6).
     */
    private void purgeTerminated(LocalDateTime now) {
        LocalDateTime cutoff = now.minusHours(72);
        personnelRepository
                .findByStatusAndTerminationTimestampBefore(PersonnelStatus.TERMINATED, cutoff)
                .forEach(p -> {
                    try {
                        // File cleanup is best-effort — a missing file must not
                        // abort the DB purge.
                        if (p.getProfilePicturePath() != null) {
                            try {
                                fileStorageService.delete(p.getProfilePicturePath());
                            } catch (Exception e) {
                                log.warn("Failed to delete picture {} for personnel {}: {}",
                                        p.getProfilePicturePath(), p.getId(), e.getMessage());
                            }
                        }
                        if (p.getProfilePictureThumbnailPath() != null) {
                            try {
                                fileStorageService.delete(p.getProfilePictureThumbnailPath());
                            } catch (Exception e) {
                                log.warn("Failed to delete thumbnail {} for personnel {}: {}",
                                        p.getProfilePictureThumbnailPath(), p.getId(), e.getMessage());
                            }
                        }

                        taskRepository.deleteByPersonnelId(p.getId());
                        salaryRepository.deleteByPersonnelId(p.getId());
                        historyRepository.deleteByPersonnelId(p.getId());
                        personnelRepository.delete(p);
                        log.info("Purged terminated personnel {} and cleaned up files", p.getId());
                    } catch (Exception e) {
                        log.error("Purge failed for personnel {}: {}", p.getId(), e.getMessage());
                    }
                });
    }
}