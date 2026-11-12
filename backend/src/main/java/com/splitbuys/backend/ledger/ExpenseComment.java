package com.splitbuys.backend.ledger;

import com.splitbuys.backend.group.ExpenseGroup;
import com.splitbuys.backend.user.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import java.time.Instant;

/** A note on an expense. Plain text, at most {@link CommentService#MAX_LENGTH} characters. */
@Entity
class ExpenseComment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private ExpenseGroup group;

    /** Not a foreign key: see the migration. */
    private long expenseId;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private AppUser author;

    @Column(nullable = false, length = 500)
    private String body;

    private Instant createdAt = Instant.now();

    protected ExpenseComment() {
    }

    ExpenseComment(ExpenseGroup group, long expenseId, AppUser author, String body) {
        this.group = group;
        this.expenseId = expenseId;
        this.author = author;
        this.body = body;
    }

    Long getId() { return id; }
    AppUser getAuthor() { return author; }
    String getBody() { return body; }
    Instant getCreatedAt() { return createdAt; }
}
