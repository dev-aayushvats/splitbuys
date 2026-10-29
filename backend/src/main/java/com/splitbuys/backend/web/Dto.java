package com.splitbuys.backend.web;

import com.splitbuys.backend.split.SplitType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Request and response shapes. All money is integer paise (INR). */
public final class Dto {
    private Dto() {
    }

    public static final long MAX_PAISE = 1_000_000_000_00L; // ₹100 crore, keeps all maths far from overflow

    // ---- requests
    public record OtpRequest(@NotBlank String email) {
    }

    public record OtpVerify(@NotBlank String email, @NotBlank String code) {
    }

    public record NameRequest(@NotBlank @Size(max = 60) String name) {
    }

    public record GroupRequest(@NotBlank @Size(max = 80) String name) {
    }

    /** value: ignored for EQUAL, paise for EXACT, percent (e.g. 33.5) for PERCENT, weight for SHARES. */
    public record SplitInput(@NotNull Long userId, BigDecimal value) {
    }

    public record ExpenseRequest(
            @NotBlank @Size(max = 120) String description,
            @Positive @Max(MAX_PAISE) long amountPaise,
            @NotNull Long payerId,
            @NotNull SplitType splitType,
            @NotEmpty @Valid List<SplitInput> splits) {
    }

    public record SettlementRequest(
            @NotNull Long fromUserId,
            @NotNull Long toUserId,
            @Positive @Max(MAX_PAISE) long amountPaise) {
    }

    // ---- responses
    public record UserDto(Long id, String email, String name) {
    }

    public record AuthResponse(String token, UserDto user, boolean newUser) {
    }

    public record GroupSummary(Long id, String name, int memberCount, long myBalancePaise) {
    }

    public record GroupDetail(Long id, String name, String inviteToken, List<UserDto> members) {
    }

    public record InvitePreview(String groupName, int memberCount, boolean alreadyMember, Long groupId) {
    }

    public record ShareDto(UserDto user, long amountPaise) {
    }

    public record ExpenseDto(Long id, String description, long amountPaise, UserDto payer, UserDto createdBy,
                             Instant createdAt, SplitType splitType, List<ShareDto> shares) {
    }

    public record SettlementDto(Long id, UserDto fromUser, UserDto toUser, long amountPaise, UserDto createdBy,
                                Instant createdAt) {
    }

    public record BalanceDto(UserDto user, long netPaise) {
    }

    public record TransferDto(UserDto from, UserDto to, long amountPaise) {
    }

    /** netPaise > 0: the member is owed money; < 0: the member owes money. */
    public record BalancesResponse(List<BalanceDto> balances, List<TransferDto> settleUp) {
    }
}
