package com.agriva.shared.kafka.diagnostics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.stereotype.Component;

import java.util.Collection;

/**
 * Diagnostic bean that logs the state of every @KafkaListener after the
 * application has finished starting. If a listener is missing from this log,
 * @EnableKafka is missing or the @KafkaListener annotation is not being scanned.
 *
 * Safe to keep in production — it only logs at INFO on startup.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaListenerDiagnostics {

    private final KafkaListenerEndpointRegistry registry;

    @EventListener(ApplicationReadyEvent.class)
    public void logListenerContainers() {
        Collection<MessageListenerContainer> containers = registry.getListenerContainers();

        if (containers.isEmpty()) {
            log.error("=================================================================");
            log.error("[KAFKA-DIAG] NO KAFKA LISTENERS REGISTERED");
            log.error("[KAFKA-DIAG] @EnableKafka is missing or @KafkaListener not scanned");
            log.error("=================================================================");
            return;
        }

        log.info("=================================================================");
        log.info("[KAFKA-DIAG] {} listener container(s) registered", containers.size());
        for (MessageListenerContainer c : containers) {
            log.info("[KAFKA-DIAG] id={} groupId={} running={} class={}",
                    c.getListenerId(),
                    c.getGroupId(),
                    c.isRunning(),
                    c.getClass().getSimpleName());
        }
        log.info("=================================================================");
    }
}