package com.splitbuys.backend.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "app_user")
public class AppUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    private String name;

    /** Where this person wants to be paid (a UPI ID such as asha@okbank). Private: see ProfileDto vs UserDto. */
    private String upiId;

    /** Set when the person deleted their account. The row stays (shared history points at it) but holds nothing personal. */
    private Instant deletedAt;

    protected AppUser() {
    }

    public AppUser(String email) {
        this.email = email;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getName() {
        return name;
    }

    public String getUpiId() {
        return upiId;
    }

    /** Pass an already-normalised ID (see UpiId), or null to remove it. */
    public void setUpiId(String upiId) {
        this.upiId = upiId;
    }

    /**
     * Erases what identifies this person. The email is replaced by a unique placeholder (the column is unique, and it frees
     * the real address so the person can sign up again later as a brand-new user); the row itself stays because expenses
     * and payments in other people's groups still refer to it and must keep adding up.
     */
    public void closeAccount(Instant now) {
        this.email = "deleted-" + id + "@deleted.invalid";
        this.name = "Deleted user";
        this.upiId = null;
        this.deletedAt = now;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void rename(String name) {
        this.name = name.trim();
    }

    // Identity is the database id; a not-yet-saved instance is only equal to itself.
    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof AppUser u && id != null && id.equals(u.getId()));
    }

    @Override
    public int hashCode() {
        return AppUser.class.hashCode();
    }
}
