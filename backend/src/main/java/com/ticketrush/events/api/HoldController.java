package com.ticketrush.events.api;

import com.ticketrush.events.HoldService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

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
}
