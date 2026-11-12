package com.splitbuys.backend.ledger;

import com.splitbuys.backend.common.error.ConflictException;
import com.splitbuys.backend.common.error.InvalidRequestException;
import com.splitbuys.backend.common.error.NotFoundException;
import com.splitbuys.backend.common.error.RateLimitedException;
import com.splitbuys.backend.group.ExpenseGroup;
import com.splitbuys.backend.group.GroupService;
import com.splitbuys.backend.user.AppUser;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Notes on an expense. Any member can read and add; only the author can delete their own. Bounded so it cannot be used to
 * fill the database: 500 characters, {@value #PER_EXPENSE} per expense, {@value #DAILY_CAP} a day per person.
 */
@Service
@Transactional
public class CommentService {
    static final int MAX_LENGTH = 500;
    static final int PER_EXPENSE = 200;
    static final int DAILY_CAP = 100;

    private final GroupService groups;
    private final ExpenseRepository expenses;
    private final ExpenseCommentRepository comments;

    CommentService(GroupService groups, ExpenseRepository expenses, ExpenseCommentRepository comments) {
        this.groups = groups;
        this.expenses = expenses;
        this.comments = comments;
    }

    /** Oldest first, like a conversation. */
    @Transactional(readOnly = true)
    public List<CommentDto> list(long groupId, long expenseId, long userId) {
        requireExpense(groupId, expenseId, userId);
        List<ExpenseComment> newestFirst = new ArrayList<>(comments.findTop200ByGroup_IdAndExpenseIdOrderByIdDesc(groupId, expenseId));
        Collections.reverse(newestFirst);
        return newestFirst.stream().map(CommentDto::from).toList();
    }

    public CommentDto add(long groupId, long expenseId, long userId, String text) {
        ExpenseGroup group = requireExpense(groupId, expenseId, userId);
        String body = text == null ? "" : text.strip();
        if (body.isEmpty()) {
            throw new InvalidRequestException("Write something first");
        }
        if (comments.countByGroup_IdAndExpenseId(groupId, expenseId) >= PER_EXPENSE) {
            throw new ConflictException("COMMENT_LIMIT", "This expense has reached the limit of " + PER_EXPENSE + " comments");
        }
        if (comments.countByAuthor_IdAndCreatedAtAfter(userId, Instant.now().minus(Duration.ofHours(24))) >= DAILY_CAP) {
            throw new RateLimitedException("You have reached today's limit of " + DAILY_CAP + " comments");
        }
        AppUser author = group.findMember(userId).orElseThrow();
        return CommentDto.from(comments.save(new ExpenseComment(group, expenseId, author, body)));
    }

    public void delete(long groupId, long commentId, long userId) {
        groups.requireMember(groupId, userId);
        ExpenseComment c = comments.findByIdAndGroup_Id(commentId, groupId)
                .orElseThrow(() -> new NotFoundException("Comment not found"));
        if (!c.getAuthor().getId().equals(userId)) {
            throw new InvalidRequestException("You can only delete your own comments");
        }
        comments.delete(c);
    }

    private ExpenseGroup requireExpense(long groupId, long expenseId, long userId) {
        ExpenseGroup group = groups.requireMember(groupId, userId);
        expenses.findByIdAndGroup_Id(expenseId, groupId).orElseThrow(() -> new NotFoundException("Expense not found"));
        return group;
    }
}
