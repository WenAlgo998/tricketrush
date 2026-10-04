package com.ticketrush.events.api;

import com.ticketrush.events.HoldService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/api/holds")
public class HoldController {

    private final HoldService holdService;

    public HoldController(HoldService holdService) {
        this.holdService = holdService;
    }

    @DeleteMapping("/{holdId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void releaseHold(@PathVariable UUID holdId, @AuthenticationPrincipal Jwt jwt) {
        holdService.release(holdId, UUID.fromString(jwt.getSubject()));
    }

    @GetMapping
    public List<ActiveHoldResponse> listActiveHolds(
            @RequestParam UUID eventId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return holdService.findActiveByEvent(eventId, UUID.fromString(jwt.getSubject())).stream()
                .map(hold -> new ActiveHoldResponse(hold.holdId(), hold.seatId(), hold.expiresAt()))
                .toList();
    }

    public record ActiveHoldResponse(UUID holdId, UUID seatId, java.time.OffsetDateTime expiresAt) {
    }
}
