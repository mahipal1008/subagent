package com.subscrub.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "subscriptions",
    indexes = {
        @Index(name = "idx_sub_user",     columnList = "user_id"),
        @Index(name = "idx_sub_next_due", columnList = "user_id, next_due_date")
    })
@Data
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false, length = 128)
    private String merchant;

    @Column(length = 64)
    private String category;

    @Column(name = "current_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal currentAmount;

    @Column(nullable = false, length = 16)
    private String frequency;       // MONTHLY | YEARLY | WEEKLY | QUARTERLY

    @Column(nullable = false, length = 16)
    private String status = "ACTIVE"; // ACTIVE | CANCELLED | PAUSED

    @Column(name = "first_seen")
    private LocalDate firstSeen;

    @Column(name = "last_seen")
    private LocalDate lastSeen;

    @Column(name = "next_due_date")
    private LocalDate nextDueDate;

    @Column(name = "avg_amount", precision = 12, scale = 2)
    private BigDecimal avgAmount;
}
