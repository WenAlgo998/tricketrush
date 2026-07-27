package com.ticketrush.common.api;

import com.ticketrush.auth.EmailAlreadyRegisteredException;
import com.ticketrush.auth.InvalidCredentialsException;
import com.ticketrush.events.EventNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MethodArgumentNotValidException;

@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(EventNotFoundException.class)
    ResponseEntity<ApiError> handleEventNotFound() {
        return ResponseEntity.status(404)
                .body(new ApiError("Event not found", "EVENT_NOT_FOUND"));
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    ResponseEntity<ApiError> handleEmailAlreadyRegistered() {
        return ResponseEntity.status(409)
                .body(new ApiError("Email is already registered", "EMAIL_ALREADY_REGISTERED"));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ResponseEntity<ApiError> handleInvalidCredentials() {
        return ResponseEntity.status(401)
                .body(new ApiError("Invalid email or password", "INVALID_CREDENTIALS"));
    }

    @ExceptionHandler({
            ConstraintViolationException.class,
            MethodArgumentTypeMismatchException.class,
            MethodArgumentNotValidException.class
    })
    ResponseEntity<ApiError> handleInvalidRequest() {
        return ResponseEntity.badRequest()
                .body(new ApiError("Request validation failed", "VALIDATION_ERROR"));
    }
}
