package com.ticketrush.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    public AuthService(
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService
    ) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
    }

    public AuthenticatedUser register(String email, String password) {
        UserAccount user = userAccountRepository.create(email, passwordEncoder.encode(password));
        return issueToken(user);
    }

    public AuthenticatedUser login(String email, String password) {
        UserAccount user = userAccountRepository.findByEmail(email)
                .filter(candidate -> passwordEncoder.matches(password, candidate.passwordHash()))
                .orElseThrow(InvalidCredentialsException::new);
        return issueToken(user);
    }

    private AuthenticatedUser issueToken(UserAccount user) {
        JwtToken token = jwtTokenService.create(user);
        return new AuthenticatedUser(user.id(), user.email(), token.value(), token.expiresInSeconds());
    }

    public record AuthenticatedUser(
            java.util.UUID id,
            String email,
            String accessToken,
            long expiresIn
    ) {
    }
}
