package com.splitbuys.backend.web;

import com.splitbuys.backend.auth.AuthInterceptor;
import com.splitbuys.backend.auth.JwtService;
import com.splitbuys.backend.auth.OtpService;
import com.splitbuys.backend.auth.OtpService.Verified;
import com.splitbuys.backend.domain.AppUser;
import com.splitbuys.backend.repo.Repos.UserRepository;
import com.splitbuys.backend.web.Dto.AuthResponse;
import com.splitbuys.backend.web.Dto.NameRequest;
import com.splitbuys.backend.web.Dto.OtpRequest;
import com.splitbuys.backend.web.Dto.OtpVerify;
import com.splitbuys.backend.web.Dto.UserDto;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {
    private final OtpService otp;
    private final JwtService jwt;
    private final UserRepository users;

    public AuthController(OtpService otp, JwtService jwt, UserRepository users) {
        this.otp = otp;
        this.jwt = jwt;
        this.users = users;
    }

    @PostMapping("/api/auth/request-otp")
    Map<String, String> requestOtp(@Valid @RequestBody OtpRequest req) {
        otp.request(req.email());
        return Map.of("status", "sent");
    }

    @PostMapping("/api/auth/verify-otp")
    AuthResponse verifyOtp(@Valid @RequestBody OtpVerify req) {
        Verified v = otp.verify(req.email(), req.code());
        return new AuthResponse(jwt.issue(v.user().id), new UserDto(v.user().id, v.user().email, v.user().name), v.newUser());
    }

    @GetMapping("/api/me")
    UserDto me(@RequestAttribute(AuthInterceptor.USER_ID) long userId) {
        AppUser u = users.findById(userId).orElseThrow(() -> ApiException.notFound("User not found"));
        return new UserDto(u.id, u.email, u.name);
    }

    @PutMapping("/api/me")
    @Transactional
    UserDto setName(@RequestAttribute(AuthInterceptor.USER_ID) long userId, @Valid @RequestBody NameRequest req) {
        AppUser u = users.findById(userId).orElseThrow(() -> ApiException.notFound("User not found"));
        u.name = req.name().trim();
        return new UserDto(u.id, u.email, u.name);
    }
}
