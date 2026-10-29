package com.splitbuys.backend.auth;

import com.splitbuys.backend.domain.AppUser;
import com.splitbuys.backend.domain.OtpCode;
import com.splitbuys.backend.repo.Repos.OtpRepository;
import com.splitbuys.backend.repo.Repos.UserRepository;
import com.splitbuys.backend.web.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OtpService {
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public record Verified(AppUser user, boolean newUser) {
    }

    private final OtpRepository otps;
    private final UserRepository users;
    private final EmailSender emailSender;
    private final SecureRandom random = new SecureRandom();
    private final byte[] secret;
    private final Duration ttl;
    private final Duration resendGap;
    private final int maxAttempts;

    public OtpService(OtpRepository otps, UserRepository users, EmailSender emailSender,
                      @Value("${app.jwt.secret}") String secret,
                      @Value("${app.otp.ttl-minutes}") long ttlMinutes,
                      @Value("${app.otp.resend-seconds}") long resendSeconds,
                      @Value("${app.otp.max-attempts}") int maxAttempts) {
        this.otps = otps;
        this.users = users;
        this.emailSender = emailSender;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.ttl = Duration.ofMinutes(ttlMinutes);
        this.resendGap = Duration.ofSeconds(resendSeconds);
        this.maxAttempts = maxAttempts;
    }

    public static String normalize(String email) {
        String e = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (!EMAIL.matcher(e).matches()) {
            throw ApiException.badRequest("Enter a valid email address");
        }
        return e;
    }

    @Transactional
    public void request(String rawEmail) {
        String email = normalize(rawEmail);
        otps.findFirstByEmailOrderByCreatedAtDesc(email).ifPresent(last -> {
            if (last.createdAt.plus(resendGap).isAfter(Instant.now())) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Please wait a moment before requesting another code");
            }
        });
        String code = String.format("%06d", random.nextInt(1_000_000));
        otps.deleteByEmail(email);
        OtpCode otp = new OtpCode();
        otp.email = email;
        otp.codeHash = hash(email, code);
        otp.expiresAt = Instant.now().plus(ttl);
        otps.save(otp);
        emailSender.sendOtp(email, code);
    }

    /** Wrong guesses must persist their attempt count, so ApiException does not roll back. */
    @Transactional(noRollbackFor = ApiException.class)
    public Verified verify(String rawEmail, String code) {
        String email = normalize(rawEmail);
        OtpCode otp = otps.findFirstByEmailOrderByCreatedAtDesc(email)
                .orElseThrow(() -> ApiException.badRequest("Request a code first"));
        if (otp.expiresAt.isBefore(Instant.now())) {
            throw ApiException.badRequest("Code expired, request a new one");
        }
        if (otp.attempts >= maxAttempts) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many attempts, request a new code");
        }
        String given = code == null ? "" : code.trim();
        if (!MessageDigest.isEqual(hash(email, given).getBytes(StandardCharsets.UTF_8),
                otp.codeHash.getBytes(StandardCharsets.UTF_8))) {
            otp.attempts++;
            otps.save(otp);
            throw ApiException.badRequest("Incorrect code");
        }
        otps.delete(otp);
        return users.findByEmail(email)
                .map(u -> new Verified(u, false))
                .orElseGet(() -> {
                    AppUser u = new AppUser();
                    u.email = email;
                    return new Verified(users.save(u), true);
                });
    }

    private String hash(String email, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((email + ":" + code).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
