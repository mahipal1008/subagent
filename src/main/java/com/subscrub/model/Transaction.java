package com.subscrub.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "transactions",
    indexes = {
        @Index(name = "idx_tx_user_date",     columnList = "user_id, date"),
        @Index(name = "idx_tx_user_merchant", columnList = "user_id, merchant")
    })
@Data
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 128)
    private String merchant;

    @Column(length = 255)
    private String description;

    @Column(length = 32)
    private String channel;         // UPI | CARD | NEFT | AUTO_DEBIT | DIRECT_DEBIT

    @Column(length = 16)
    private String currency = "INR";

    @Column(name = "raw_entry", columnDefinition = "TEXT")
    private String rawEntry;
}
