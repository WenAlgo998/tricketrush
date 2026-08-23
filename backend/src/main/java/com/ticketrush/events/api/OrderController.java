package com.ticketrush.events.api;

import com.ticketrush.events.CheckoutService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final CheckoutService checkoutService;

    public OrderController(CheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public OrderResponse checkout(
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @Valid @RequestBody CheckoutRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        CheckoutService.PendingOrder order = checkoutService.checkout(
                UUID.fromString(jwt.getSubject()), idempotencyKey, request.holdIds()
        );
        return new OrderResponse(order.orderId(), order.status());
    }

    public record CheckoutRequest(@NotEmpty @Size(max = 10) List<@NotNull UUID> holdIds) {
    }

    public record OrderResponse(UUID orderId, String status) {
    }
}
