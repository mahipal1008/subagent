package com.subscrub.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;

@Entity
@Table(name = "known_services",
    indexes = {
        @Index(name = "idx_ks_pattern", columnList = "merchant_pattern")
    })
@Data
public class KnownService {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "service_name", nullable = false, length = 100)
    private String serviceName;

    @Column(name = "merchant_pattern", nullable = false, length = 200)
    private String merchantPattern;

    @Column(length = 50)
    private String category;

    @Column(name = "typical_amount_min", precision = 10, scale = 2)
    private BigDecimal typicalAmountMin;

    @Column(name = "typical_amount_max", precision = 10, scale = 2)
    private BigDecimal typicalAmountMax;

    @Column(length = 20)
    private String frequency;

    @Column(length = 200)
    private String website;

    @Column(name = "is_active")
    private boolean isActive = true;
}
