package com.agriva.shared.utils;

public record BatchParsedRow<R>(int rowNumber, R request, String error) {

    public boolean isError() {
        return error != null;
    }
}