package com.subscrub.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "monthly_summaries",
    indexes = {
        @Index(name = "idx_ms_user_month", columnList = "user_id, summary_month"),
        @Index(name = "idx_ms_sub_id",     columnList = "subscription_id")
    })
@Data
public class MonthlySummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "subscription_id")
    private Long subscriptionId;

    @Column(name = "summary_month", nullable = false)
    private LocalDate month;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "charge_count")
    private int chargeCount;

    @Column(length = 50)
    private String category;
}
