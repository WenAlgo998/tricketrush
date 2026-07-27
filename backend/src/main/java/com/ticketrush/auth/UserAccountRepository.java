package com.ticketrush.auth;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Locale;
import java.util.Optional;

@Repository
public class UserAccountRepository {

    private final JdbcTemplate jdbcTemplate;

    public UserAccountRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public UserAccount create(String email, String passwordHash) {
        try {
            return jdbcTemplate.queryForObject("""
                    INSERT INTO users (email, password_hash)
                    VALUES (?, ?)
                    RETURNING id, email, password_hash
                    """, (resultSet, rowNum) -> new UserAccount(
                    resultSet.getObject("id", java.util.UUID.class),
                    resultSet.getString("email"),
                    resultSet.getString("password_hash")
            ), normalizeEmail(email), passwordHash);
        } catch (DuplicateKeyException exception) {
            throw new EmailAlreadyRegisteredException();
        }
    }

    public Optional<UserAccount> findByEmail(String email) {
        return jdbcTemplate.query("""
                        SELECT id, email, password_hash
                        FROM users
                        WHERE email = ?
                        """, (resultSet, rowNum) -> new UserAccount(
                        resultSet.getObject("id", java.util.UUID.class),
                        resultSet.getString("email"),
                        resultSet.getString("password_hash")
                ), normalizeEmail(email))
                .stream()
                .findFirst();
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
