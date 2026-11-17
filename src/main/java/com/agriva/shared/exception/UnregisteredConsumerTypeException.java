package com.agriva.shared.exception;

import lombok.Getter;

@Getter
public class UnregisteredConsumerTypeException extends RuntimeException {
    private final String consumerType;

    public UnregisteredConsumerTypeException(String consumerType) {
        super("Consumer type " + consumerType + " is not registered");
        this.consumerType = consumerType;
    }

}