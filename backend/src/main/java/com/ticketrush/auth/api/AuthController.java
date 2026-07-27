package com.ticketrush.auth.api;

import com.ticketrush.auth.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody CredentialsRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(AuthResponse.from(
                authService.register(request.email(), request.password())
        ));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody CredentialsRequest request) {
        return AuthResponse.from(authService.login(request.email(), request.password()));
    }

    public record CredentialsRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(min = 8, max = 72) String password
    ) {
    }

    public record AuthResponse(
            UUID userId,
            String email,
            String accessToken,
            String tokenType,
            long expiresIn
    ) {
        private static AuthResponse from(AuthService.AuthenticatedUser user) {
            return new AuthResponse(user.id(), user.email(), user.accessToken(), "Bearer", user.expiresIn());
        }
    }
}
