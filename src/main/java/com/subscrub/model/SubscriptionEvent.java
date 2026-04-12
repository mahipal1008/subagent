package com.subscrub.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "subscription_events",
    indexes = {
        @Index(name = "idx_se_sub_id",    columnList = "subscription_id"),
        @Index(name = "idx_se_user_date", columnList = "user_id, date")
    })
@Data
public class SubscriptionEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "subscription_id", nullable = false)
    private Long subscriptionId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "source_transaction_id")
    private Long sourceTransactionId;

    @Column(name = "anomaly_flag")
    private boolean anomalyFlag = false;

    @Column(name = "amount_delta", precision = 12, scale = 2)
    private BigDecimal amountDelta;

    @Column(name = "amount_delta_pct", precision = 6, scale = 2)
    private BigDecimal amountDeltaPct;
}
