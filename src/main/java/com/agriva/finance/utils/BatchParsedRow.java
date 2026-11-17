package com.agriva.finance.utils;

public record BatchParsedRow<R>(int rowNumber, R request, String error) {

    public boolean isError() {
        return error != null;
    }
}