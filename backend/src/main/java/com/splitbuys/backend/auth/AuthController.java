package com.splitbuys.backend.auth;

import com.splitbuys.backend.user.UserDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
class AuthController {
    record OtpRequest(@NotBlank String email) {
    }

    record OtpVerify(@NotBlank String email, @NotBlank String code) {
    }

    record AuthResponse(String token, UserDto user, boolean newUser) {
    }

    private final OtpService otp;
    private final TokenService tokens;

    AuthController(OtpService otp, TokenService tokens) {
        this.otp = otp;
        this.tokens = tokens;
    }

    @PostMapping("/request-otp")
    Map<String, String> requestOtp(@Valid @RequestBody OtpRequest req) {
        otp.request(req.email());
        return Map.of("status", "sent");
    }

    @PostMapping("/verify-otp")
    AuthResponse verifyOtp(@Valid @RequestBody OtpVerify req) {
        OtpService.Verified v = otp.verify(req.email(), req.code());
        return new AuthResponse(tokens.issue(v.user().getId()), UserDto.from(v.user()), v.newUser());
    }
}
