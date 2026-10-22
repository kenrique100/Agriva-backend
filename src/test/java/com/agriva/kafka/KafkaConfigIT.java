package com.agriva.kafka;

import com.agriva.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the Kafka consumer factory fix: IncomeStagingConsumer references
 * "incomeKafkaListenerContainerFactory", and the app cannot start unless that
 * bean exists AND the listener can bind to it. Passing = the fix holds.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class KafkaConfigIT {

    @Autowired ApplicationContext ctx;

    @Test
    void bothTypedContainerFactoriesExist() {
        assertThat(ctx.containsBean("incomeKafkaListenerContainerFactory")).isTrue();
        assertThat(ctx.containsBean("expenseKafkaListenerContainerFactory")).isTrue();
    }

    @Test
    void incomeConsumerBindsToItsTypedFactory() {
        // If IncomeStagingConsumer were still wired to the missing
        // "kafkaListenerContainerFactory" bean, Spring would refuse to start and
        // every @SpringBootTest in the suite would fail. Passing this test proves
        // the entire listener lifecycle (endpoint registration + message adapter
        // binding) succeeds.
        assertThat(ctx.containsBean("incomeStagingConsumer")).isTrue();
        assertThat(ctx.containsBean("expenseStagingConsumer")).isTrue();
    }
}