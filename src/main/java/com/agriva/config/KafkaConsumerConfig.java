package com.agriva.config;

import com.agriva.model.dto.expense.ExpenseSubmissionEvent;
import com.agriva.model.dto.income.IncomeSubmissionEvent;
import com.agriva.model.dto.crop.CropCycleSubmissionEvent;
import com.agriva.model.dto.salary.SalarySubmissionEvent;
import com.agriva.model.dto.task.TaskSubmissionEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ConsumerFactory<String, IncomeSubmissionEvent> incomeConsumerFactory() {
        Map<String, Object> props = baseProps();
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "agriva-income-staging-consumer");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, IncomeSubmissionEvent.class.getName());
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConsumerFactory<String, CropCycleSubmissionEvent> cropConsumerFactory() {
        Map<String, Object> props = baseProps();
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "agriva-crop-staging-consumer");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, CropCycleSubmissionEvent.class.getName());
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, CropCycleSubmissionEvent>
    cropKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, CropCycleSubmissionEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(cropConsumerFactory());
        factory.setConcurrency(3);
        return factory;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, IncomeSubmissionEvent>
    incomeKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, IncomeSubmissionEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(incomeConsumerFactory());
        factory.setConcurrency(3);
        return factory;
    }

    @Bean
    public ConsumerFactory<String, ExpenseSubmissionEvent> expenseConsumerFactory() {
        Map<String, Object> props = baseProps();
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "agriva-expense-staging-consumer");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, ExpenseSubmissionEvent.class.getName());
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ExpenseSubmissionEvent>
    expenseKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, ExpenseSubmissionEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(expenseConsumerFactory());
        factory.setConcurrency(3);
        return factory;
    }

    @Bean
    public ConsumerFactory<String, TaskSubmissionEvent> taskConsumerFactory() {
        Map<String, Object> props = baseProps();
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "agriva-task-staging-consumer");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, TaskSubmissionEvent.class.getName());
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, TaskSubmissionEvent>
    taskKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, TaskSubmissionEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(taskConsumerFactory());
        factory.setConcurrency(3);
        return factory;
    }

    @Bean
    public ConsumerFactory<String, SalarySubmissionEvent> salaryConsumerFactory() {
        Map<String, Object> props = baseProps();
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "agriva-salary-staging-consumer");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, SalarySubmissionEvent.class.getName());
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, SalarySubmissionEvent>
    salaryKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, SalarySubmissionEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(salaryConsumerFactory());
        factory.setConcurrency(3);
        return factory;
    }

    private Map<String, Object> baseProps() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
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