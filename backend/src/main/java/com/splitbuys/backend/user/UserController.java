package com.splitbuys.backend.user;

import com.splitbuys.backend.common.security.CurrentUser;
import jakarta.validation.Valid;
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
    UserDto me(@CurrentUser long userId) {
        return users.get(userId);
    }

    @PutMapping
    UserDto rename(@CurrentUser long userId, @Valid @RequestBody NameRequest req) {
        return users.rename(userId, req.name());
    }
}
