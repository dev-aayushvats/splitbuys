package com.splitbuys.backend.web;

import com.splitbuys.backend.auth.AuthInterceptor;
import com.splitbuys.backend.service.GroupService;
import com.splitbuys.backend.web.Dto.BalancesResponse;
import com.splitbuys.backend.web.Dto.GroupDetail;
import com.splitbuys.backend.web.Dto.GroupRequest;
import com.splitbuys.backend.web.Dto.GroupSummary;
import com.splitbuys.backend.web.Dto.InvitePreview;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GroupController {
    private final GroupService groups;

    public GroupController(GroupService groups) {
        this.groups = groups;
    }

    @PostMapping("/api/groups")
    @ResponseStatus(HttpStatus.CREATED)
    GroupDetail create(@RequestAttribute(AuthInterceptor.USER_ID) long userId, @Valid @RequestBody GroupRequest req) {
        return groups.create(userId, req.name());
    }

    @GetMapping("/api/groups")
    List<GroupSummary> list(@RequestAttribute(AuthInterceptor.USER_ID) long userId) {
        return groups.list(userId);
    }

    @GetMapping("/api/groups/{id}")
    GroupDetail get(@RequestAttribute(AuthInterceptor.USER_ID) long userId, @PathVariable long id) {
        return groups.get(id, userId);
    }

    @PostMapping("/api/groups/{id}/invite/regenerate")
    GroupDetail regenerateInvite(@RequestAttribute(AuthInterceptor.USER_ID) long userId, @PathVariable long id) {
        return groups.regenerateInvite(id, userId);
    }

    @DeleteMapping("/api/groups/{id}/members/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void leave(@RequestAttribute(AuthInterceptor.USER_ID) long userId, @PathVariable long id) {
        groups.leave(id, userId);
    }

    @GetMapping("/api/groups/{id}/balances")
    BalancesResponse balances(@RequestAttribute(AuthInterceptor.USER_ID) long userId, @PathVariable long id) {
        return groups.balances(id, userId);
    }

    @GetMapping("/api/invites/{token}")
    InvitePreview previewInvite(@RequestAttribute(AuthInterceptor.USER_ID) long userId, @PathVariable String token) {
        return groups.previewInvite(token, userId);
    }

    @PostMapping("/api/invites/{token}/join")
    GroupDetail join(@RequestAttribute(AuthInterceptor.USER_ID) long userId, @PathVariable String token) {
        return groups.join(token, userId);
    }
}
