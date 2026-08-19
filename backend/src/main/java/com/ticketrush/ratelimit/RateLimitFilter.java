package com.ticketrush.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketrush.common.api.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> MUTATING_METHODS = Set.of(
            HttpMethod.POST.name(), HttpMethod.PUT.name(), HttpMethod.PATCH.name(), HttpMethod.DELETE.name()
    );

    private final RedisRateLimiter redisRateLimiter;
    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(
            RedisRateLimiter redisRateLimiter,
            RateLimitProperties properties,
            ObjectMapper objectMapper
    ) {
        this.redisRateLimiter = redisRateLimiter;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.enabled()
                || !MUTATING_METHODS.contains(request.getMethod())
                || !request.getRequestURI().startsWith("/api/")
                || request.getRequestURI().startsWith("/api/auth/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            filterChain.doFilter(request, response);
            return;
        }

        RateLimitDecision decision = redisRateLimiter.check(jwtAuthentication.getToken().getSubject());
        if (decision.allowed()) {
            filterChain.doFilter(request, response);
            return;
        }

        writeRejectedResponse(response, decision.retryAfter());
    }

    private void writeRejectedResponse(HttpServletResponse response, Duration retryAfter) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        long retryAfterSeconds = Math.max(1, (retryAfter.toMillis() + 999) / 1_000);
        response.setHeader("Retry-After", Long.toString(retryAfterSeconds));
        objectMapper.writeValue(response.getOutputStream(), new ApiError("Rate limit exceeded", "RATE_LIMIT_EXCEEDED"));
    }
}
