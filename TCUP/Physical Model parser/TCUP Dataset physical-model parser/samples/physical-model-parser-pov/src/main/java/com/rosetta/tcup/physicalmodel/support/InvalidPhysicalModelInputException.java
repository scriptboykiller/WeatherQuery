package com.rosetta.tcup.physicalmodel.support;

public class InvalidPhysicalModelInputException extends RuntimeException {
    public InvalidPhysicalModelInputException(String message) {
        super(message);
    }

    public InvalidPhysicalModelInputException(String message, Throwable cause) {
        super(message, cause);
    }
}
