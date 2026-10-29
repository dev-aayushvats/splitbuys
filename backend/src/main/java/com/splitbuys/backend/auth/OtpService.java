package com.splitbuys.backend.auth;

import com.splitbuys.backend.common.error.InvalidRequestException;
import com.splitbuys.backend.common.error.RateLimitedException;
import com.splitbuys.backend.user.UserService;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** One-time sign-in codes: issue, deliver, verify. Codes are stored only as keyed hashes. */
@Service
public class OtpService {
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    /** The account a verified code resolves to. */
    public record Verified(com.splitbuys.backend.user.AppUser user, boolean newUser) {
    }

    private final OtpRepository otps;
    private final UserService users;
    private final EmailSender emailSender;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final SecretKey hashKey;
    private final Duration ttl;
    private final Duration resendGap;
    private final int maxAttempts;

    OtpService(OtpRepository otps, UserService users, EmailSender emailSender, PlatformTransactionManager txManager,
               Clock clock, SecretKey jwtKey,
               @Value("${app.otp.ttl-minutes}") long ttlMinutes,
               @Value("${app.otp.resend-seconds}") long resendSeconds,
               @Value("${app.otp.max-attempts}") int maxAttempts) {
        this.otps = otps;
        this.users = users;
        this.emailSender = emailSender;
        this.tx = new TransactionTemplate(txManager);
        this.clock = clock;
        // key separation: the OTP hash key is derived from, but never equal to, the token-signing key
        this.hashKey = derive(jwtKey, "splitbuys/otp/v1");
        this.ttl = Duration.ofMinutes(ttlMinutes);
        this.resendGap = Duration.ofSeconds(resendSeconds);
        this.maxAttempts = maxAttempts;
    }

    public static String normalize(String email) {
        String e = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (!EMAIL.matcher(e).matches()) {
            throw new InvalidRequestException("Enter a valid email address");
        }
        return e;
    }

    /**
     * Stores a fresh code, then sends it. The email goes out <em>after</em> the transaction commits so a slow SMTP
     * server never holds a database connection; if delivery fails the code is withdrawn so the user can retry at once.
     */
    public void request(String rawEmail) {
        String email = normalize(rawEmail);
        String code = tx.execute(status -> issue(email));
        try {
            emailSender.sendOtp(email, code);
        } catch (RuntimeException e) {
            tx.executeWithoutResult(status -> otps.deleteByEmail(email));
            throw e;
        }
    }

    private String issue(String email) {
        Instant now = clock.instant();
        otps.findFirstByEmailOrderByCreatedAtDesc(email).ifPresent(last -> {
            if (last.getCreatedAt().plus(resendGap).isAfter(now)) {
                throw new RateLimitedException("Please wait a moment before requesting another code");
            }
        });
        String code = String.format("%06d", random.nextInt(1_000_000));
        otps.deleteByEmail(email);
        otps.save(new OtpCode(email, hash(email, code), now, now.plus(ttl)));
        return code;
    }

    /** Wrong guesses must persist their attempt count, so domain exceptions do not roll back. */
    @Transactional(noRollbackFor = com.splitbuys.backend.common.error.DomainException.class)
    public Verified verify(String rawEmail, String code) {
        String email = normalize(rawEmail);
        OtpCode otp = otps.findFirstByEmailOrderByCreatedAtDesc(email)
                .orElseThrow(() -> new InvalidRequestException("Request a code first"));
        if (otp.getExpiresAt().isBefore(clock.instant())) {
            throw new InvalidRequestException("Code expired, request a new one");
        }
        if (otp.getAttempts() >= maxAttempts) {
            throw new RateLimitedException("Too many attempts, request a new code");
        }
        String given = code == null ? "" : code.trim();
        if (!MessageDigest.isEqual(hash(email, given).getBytes(StandardCharsets.UTF_8),
                otp.getCodeHash().getBytes(StandardCharsets.UTF_8))) {
            otp.recordFailedAttempt();
            throw new InvalidRequestException("Incorrect code");
        }
        otps.delete(otp);
        UserService.Resolved resolved = users.findOrCreateByEmail(email);
        return new Verified(resolved.user(), resolved.created());
    }

    /** Housekeeping: spent and expired codes would otherwise accumulate forever. */
    @Transactional
    public int purgeExpired() {
        return otps.deleteExpiredBefore(clock.instant());
    }

    private String hash(String email, String code) {
        return HexFormat.of().formatHex(hmac(hashKey, email + ":" + code));
    }

    private static SecretKey derive(SecretKey master, String label) {
        return new SecretKeySpec(hmac(master, label), "HmacSHA256");
    }

    private static byte[] hmac(SecretKey key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
