package com.rosetta.tcup.physicalmodel.support;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler({InvalidPhysicalModelInputException.class, MethodArgumentNotValidException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Map<String, String> invalidInput(Exception exception) {
        String message = exception instanceof InvalidPhysicalModelInputException
                ? exception.getMessage()
                : "sourceType and non-blank content are required";
        return Map.of("code", "INVALID_INPUT", "message", message);
    }
}
