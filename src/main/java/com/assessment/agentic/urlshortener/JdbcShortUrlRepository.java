package com.assessment.agentic.urlshortener;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcShortUrlRepository {

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public JdbcShortUrlRepository(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    public Optional<ShortUrlRecord> findByCode(String code) {
        return jdbcTemplate.query("select * from short_urls where short_code = ?", this::map, code).stream().findFirst();
    }

    public ShortUrlRecord create(String code, String originalUrl, Instant expiresAt) {
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        try {
            jdbcTemplate.update(
                """
                insert into short_urls (id, short_code, original_url, active, expires_at, created_at, updated_at, redirect_count)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                code,
                originalUrl,
                true,
                expiresAt == null ? null : Timestamp.from(expiresAt),
                Timestamp.from(now),
                Timestamp.from(now),
                0
            );
        } catch (DuplicateKeyException exception) {
            throw exception;
        }
        return findByCode(code).orElseThrow();
    }

    @Transactional
    public void recordRedirect(UUID shortUrlId) {
        Instant now = clock.instant();
        jdbcTemplate.update("insert into redirect_events (id, short_url_id, occurred_at) values (?, ?, ?)",
            UUID.randomUUID(), shortUrlId, Timestamp.from(now));
        jdbcTemplate.update("update short_urls set redirect_count = redirect_count + 1, updated_at = ? where id = ?",
            Timestamp.from(now), shortUrlId);
    }

    public void deactivate(String code) {
        int updated = jdbcTemplate.update("update short_urls set active = false, updated_at = ? where short_code = ?",
            Timestamp.from(clock.instant()), code);
        if (updated == 0) {
            throw new UrlShortenerException("SHORT_CODE_NOT_FOUND", "Short code was not found.");
        }
    }

    private ShortUrlRecord map(ResultSet rs, int rowNum) throws SQLException {
        Timestamp expiresAt = rs.getTimestamp("expires_at");
        return new ShortUrlRecord(
            rs.getObject("id", UUID.class),
            rs.getString("short_code"),
            rs.getString("original_url"),
            rs.getBoolean("active"),
            expiresAt == null ? null : expiresAt.toInstant(),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant(),
            rs.getLong("redirect_count")
        );
    }
}
