package com.agriva.shared.exception;

public class UnregisteredConsumerTypeException extends RuntimeException {
    private final String consumerType;

    public UnregisteredConsumerTypeException(String consumerType) {
        super("Consumer type " + consumerType + " is not registered");
        this.consumerType = consumerType;
    }

    public String getConsumerType() { return consumerType; }
}