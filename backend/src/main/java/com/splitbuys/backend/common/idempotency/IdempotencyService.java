package com.splitbuys.backend.common.idempotency;

import com.splitbuys.backend.common.error.ConflictException;
import com.splitbuys.backend.common.error.InvalidRequestException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Makes a create request safe to repeat. The client sends an {@code Idempotency-Key}; the first request does the work and
 * files the response under (user, key), and any repeat with the same key gets that response back instead of doing the work
 * again.
 *
 * The record is written in the same transaction as the work itself (hence MANDATORY): either both are saved or neither is.
 * Two identical requests racing each other cannot both succeed, because the unique index on (user, key) rejects the second
 * insert and rolls back its work too.
 */
@Service
public class IdempotencyService {
    /** Long enough for a UUID, short enough that nobody stores a payload in it. */
    private static final Pattern VALID_KEY = Pattern.compile("[A-Za-z0-9_-]{8,64}");
    static final Duration RETAIN = Duration.ofHours(24);

    private final IdempotencyRepository records;
    private final JsonMapper json;

    IdempotencyService(IdempotencyRepository records, JsonMapper json) {
        this.records = records;
        this.json = json;
    }

    /**
     * @param scope   names the operation (for example "expense:12") so a key cannot be replayed against another endpoint
     * @param request what the client asked for; compared on a repeat to detect a reused key
     * @param key     the client's Idempotency-Key, or null, in which case the work simply runs as before
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public <T> T once(long userId, String key, String scope, Object request, Class<T> responseType, Supplier<T> work) {
        if (key == null) {
            return work.get();
        }
        if (!VALID_KEY.matcher(key).matches()) {
            throw new InvalidRequestException("Idempotency-Key must be 8-64 letters, digits, '-' or '_'");
        }
        String fingerprint = fingerprint(scope, request);

        var earlier = records.findByUserIdAndKeyValue(userId, key);
        if (earlier.isPresent()) {
            if (!earlier.get().getFingerprint().equals(fingerprint)) {
                throw new ConflictException("IDEMPOTENCY_KEY_REUSED",
                        "That Idempotency-Key was already used for a different request.");
            }
            return json.readValue(earlier.get().getResponse(), responseType);
        }

        T result = work.get();
        try {
            records.saveAndFlush(new IdempotencyRecord(userId, key, fingerprint, json.writeValueAsString(result)));
        } catch (DataIntegrityViolationException raced) {
            // an identical request got in first; this transaction (including its work) is rolled back
            throw new ConflictException("REQUEST_IN_PROGRESS", "This request is already being processed. Try again in a moment.");
        }
        return result;
    }

    /** Drops everything remembered for a user (used when their account is deleted). */
    @Transactional
    public void forgetUser(long userId) {
        records.deleteByUserId(userId);
    }

    @Transactional
    public int purgeOld() {
        return records.deleteOlderThan(Instant.now().minus(RETAIN));
    }

    private String fingerprint(String scope, Object request) {
        try {
            byte[] bytes = (scope + "\n" + json.writeValueAsString(request)).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
