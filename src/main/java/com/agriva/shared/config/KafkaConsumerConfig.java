package com.agriva.shared.config;

import com.agriva.farming.model.dto.requests.CropCycleSubmissionEvent;
import com.agriva.finance.model.dto.request.ExpenseSubmissionEvent;
import com.agriva.finance.model.dto.request.IncomeSubmissionEvent;
import com.agriva.payroll.model.dto.requests.SalarySubmissionEvent;
import com.agriva.task.model.dto.requests.TaskSubmissionEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaConnectionDetails;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
public class KafkaConsumerConfig {

    private final KafkaConnectionDetails kafkaConnectionDetails;

    @Value("${agriva.kafka.concurrency:3}")
    private int concurrency;
    @Value("${agriva.kafka.listener.error-handler.max-retries:3}")
    private long maxRetries;
    @Value("${agriva.kafka.listener.error-handler.backoff-ms:1000}")
    private long backoffMs;

    public KafkaConsumerConfig(KafkaConnectionDetails kafkaConnectionDetails) {
        this.kafkaConnectionDetails = kafkaConnectionDetails;
    }

    @Bean("dlqProducerFactory")
    public ProducerFactory<String, Object> dlqProducerFactory() {
        Map<String, Object> props = new HashMap<>();
        List<String> bootstrap = kafkaConnectionDetails.getBootstrapServers();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean("dlqKafkaTemplate")
    public KafkaTemplate<String, Object> dlqKafkaTemplate(
            @org.springframework.beans.factory.annotation.Qualifier("dlqProducerFactory")
            ProducerFactory<String, Object> dlqProducerFactory) {
        return new KafkaTemplate<>(dlqProducerFactory);
    }

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(
            @org.springframework.beans.factory.annotation.Qualifier("dlqKafkaTemplate")
            KafkaTemplate<String, Object> dlqKafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                dlqKafkaTemplate,
                (consumerRecord, ex) -> new TopicPartition(consumerRecord.topic() + ".dlq", -1));

        DefaultErrorHandler handler =
                new DefaultErrorHandler(recoverer, new FixedBackOff(backoffMs, maxRetries));
        handler.addNotRetryableExceptions(
                DeserializationException.class,
                SerializationException.class);
        return handler;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, IncomeSubmissionEvent>
    incomeKafkaListenerContainerFactory(DefaultErrorHandler handler) {
        var f = new ConcurrentKafkaListenerContainerFactory<String, IncomeSubmissionEvent>();
        f.setConsumerFactory(incomeConsumerFactory());
        f.setConcurrency(concurrency);
        f.setCommonErrorHandler(handler);
        return f;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ExpenseSubmissionEvent>
    expenseKafkaListenerContainerFactory(DefaultErrorHandler handler) {
        var f = new ConcurrentKafkaListenerContainerFactory<String, ExpenseSubmissionEvent>();
        f.setConsumerFactory(expenseConsumerFactory());
        f.setConcurrency(concurrency);
        f.setCommonErrorHandler(handler);
        return f;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, TaskSubmissionEvent>
    taskKafkaListenerContainerFactory(DefaultErrorHandler handler) {
        var f = new ConcurrentKafkaListenerContainerFactory<String, TaskSubmissionEvent>();
        f.setConsumerFactory(taskConsumerFactory());
        f.setConcurrency(concurrency);
        f.setCommonErrorHandler(handler);
        return f;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, SalarySubmissionEvent>
    salaryKafkaListenerContainerFactory(DefaultErrorHandler handler) {
        var f = new ConcurrentKafkaListenerContainerFactory<String, SalarySubmissionEvent>();
        f.setConsumerFactory(salaryConsumerFactory());
        f.setConcurrency(concurrency);
        f.setCommonErrorHandler(handler);
        return f;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, CropCycleSubmissionEvent>
    cropKafkaListenerContainerFactory(DefaultErrorHandler handler) {
        var f = new ConcurrentKafkaListenerContainerFactory<String, CropCycleSubmissionEvent>();
        f.setConsumerFactory(cropConsumerFactory());
        f.setConcurrency(concurrency);
        f.setCommonErrorHandler(handler);
        return f;
    }

    @Bean public ConsumerFactory<String, IncomeSubmissionEvent> incomeConsumerFactory() {
        Map<String, Object> props = baseProps("agriva-income-staging-consumer");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, IncomeSubmissionEvent.class.getName());
        return new DefaultKafkaConsumerFactory<>(props);
    }
    @Bean public ConsumerFactory<String, ExpenseSubmissionEvent> expenseConsumerFactory() {
        Map<String, Object> props = baseProps("agriva-expense-staging-consumer");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, ExpenseSubmissionEvent.class.getName());
        return new DefaultKafkaConsumerFactory<>(props);
    }
    @Bean public ConsumerFactory<String, TaskSubmissionEvent> taskConsumerFactory() {
        Map<String, Object> props = baseProps("agriva-task-staging-consumer");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, TaskSubmissionEvent.class.getName());
        return new DefaultKafkaConsumerFactory<>(props);
    }
    @Bean public ConsumerFactory<String, SalarySubmissionEvent> salaryConsumerFactory() {
        Map<String, Object> props = baseProps("agriva-salary-staging-consumer");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, SalarySubmissionEvent.class.getName());
        return new DefaultKafkaConsumerFactory<>(props);
    }
    @Bean public ConsumerFactory<String, CropCycleSubmissionEvent> cropConsumerFactory() {
        Map<String, Object> props = baseProps("agriva-crop-staging-consumer");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, CropCycleSubmissionEvent.class.getName());
        return new DefaultKafkaConsumerFactory<>(props);
    }

    private Map<String, Object> baseProps(String groupId) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaConnectionDetails.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class.getName());
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.agriva.*");
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);
        return props;
    }
}