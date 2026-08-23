package com.ticketrush.common.api;

import com.ticketrush.auth.EmailAlreadyRegisteredException;
import com.ticketrush.auth.InvalidCredentialsException;
import com.ticketrush.events.EventNotFoundException;
import com.ticketrush.events.EventNotOnSaleException;
import com.ticketrush.events.SeatUnavailableException;
import com.ticketrush.events.SeatHoldConflictException;
import com.ticketrush.events.HoldNotOwnedException;
import com.ticketrush.events.CheckoutHoldConflictException;
import com.ticketrush.waitingroom.WaitingRoomUnavailableException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.http.converter.HttpMessageNotReadableException;

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

    @ExceptionHandler(EventNotOnSaleException.class)
    ResponseEntity<ApiError> handleEventNotOnSale() {
        return ResponseEntity.status(409)
                .body(new ApiError("Event is not on sale", "EVENT_NOT_ON_SALE"));
    }

    @ExceptionHandler(SeatUnavailableException.class)
    ResponseEntity<ApiError> handleSeatUnavailable() {
        return ResponseEntity.status(409)
                .body(new ApiError("Seat is no longer available", "SEAT_UNAVAILABLE"));
    }

    @ExceptionHandler(SeatHoldConflictException.class)
    ResponseEntity<ApiError> handleSeatHoldConflict() {
        return ResponseEntity.status(409)
                .body(new ApiError("Seat is no longer available", "SEAT_HOLD_CONFLICT"));
    }

    @ExceptionHandler(HoldNotOwnedException.class)
    ResponseEntity<ApiError> handleHoldNotOwned() {
        return ResponseEntity.status(403)
                .body(new ApiError("Hold is not owned by the authenticated buyer", "HOLD_NOT_OWNED"));
    }

    @ExceptionHandler(CheckoutHoldConflictException.class)
    ResponseEntity<ApiError> handleCheckoutHoldConflict() {
        return ResponseEntity.status(409)
                .body(new ApiError("One or more holds are not active and owned by the authenticated buyer", "CHECKOUT_HOLD_CONFLICT"));
    }

    @ExceptionHandler(WaitingRoomUnavailableException.class)
    ResponseEntity<ApiError> handleWaitingRoomUnavailable() {
        return ResponseEntity.status(503)
                .body(new ApiError("Waiting room is temporarily unavailable", "WAITING_ROOM_UNAVAILABLE"));
    }

    @ExceptionHandler({
            ConstraintViolationException.class,
            MethodArgumentTypeMismatchException.class,
            MethodArgumentNotValidException.class,
            MissingRequestHeaderException.class,
            HttpMessageNotReadableException.class,
            IllegalArgumentException.class
    })
    ResponseEntity<ApiError> handleInvalidRequest() {
        return ResponseEntity.badRequest()
                .body(new ApiError("Request validation failed", "VALIDATION_ERROR"));
    }
}
