package com.agriva.shared.exception;

import lombok.Getter;

import java.util.UUID;

@Getter
public class ConsumerNotFoundInDomainException extends RuntimeException {
    private final String consumerType;
    private final UUID consumerId;

    public ConsumerNotFoundInDomainException(String consumerType, UUID consumerId) {
        super("Consumer " + consumerType + "/" + consumerId + " not found in its domain");
        this.consumerType = consumerType;
        this.consumerId = consumerId;
    }

}