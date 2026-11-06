package com.splitbuys.backend.user;

import com.splitbuys.backend.common.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
class UserController {
    private final UserService users;

    UserController(UserService users) {
        this.users = users;
    }

    @GetMapping
    ProfileDto me(@CurrentUser long userId) {
        return users.get(userId);
    }

    @PutMapping
    ProfileDto rename(@CurrentUser long userId, @Valid @RequestBody NameRequest req) {
        return users.rename(userId, req.name());
    }

    @PutMapping("/upi")
    ProfileDto setUpi(@CurrentUser long userId, @Valid @RequestBody UpiRequest req) {
        return users.setUpiId(userId, req.upiId());
    }

    @DeleteMapping("/upi")
    ProfileDto clearUpi(@CurrentUser long userId) {
        return users.clearUpiId(userId);
    }
}
