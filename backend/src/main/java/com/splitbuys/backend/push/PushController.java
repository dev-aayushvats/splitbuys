package com.splitbuys.backend.push;

import com.splitbuys.backend.common.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/push")
class PushController {
    private final PushService push;

    PushController(PushService push) {
        this.push = push;
    }

    /** Tells the app whether to offer notifications at all, and the public key a browser needs to subscribe. */
    record Config(boolean enabled, String publicKey) {
    }

    /** The shape browsers produce with PushSubscription.toJSON(). */
    record SubscribeRequest(@NotBlank @Size(max = PushEndpoints.MAX_LENGTH) String endpoint, @NotNull @Valid Keys keys) {
        record Keys(@NotBlank @Size(max = 128) String p256dh, @NotBlank @Size(max = 64) String auth) {
        }
    }

    record UnsubscribeRequest(@NotBlank @Size(max = PushEndpoints.MAX_LENGTH) String endpoint) {
    }

    @GetMapping("/config")
    Config config() {
        return new Config(push.enabled(), push.enabled() ? push.publicKey() : null);
    }

    @PostMapping("/subscriptions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void subscribe(@CurrentUser long userId, @Valid @RequestBody SubscribeRequest req) {
        push.subscribe(userId, req.endpoint(), req.keys().p256dh(), req.keys().auth());
    }

    @DeleteMapping("/subscriptions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unsubscribe(@CurrentUser long userId, @Valid @RequestBody UnsubscribeRequest req) {
        push.unsubscribe(userId, req.endpoint());
    }
}
