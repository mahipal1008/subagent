package com.subscrub.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "query_lineage",
    indexes = {
        @Index(name = "idx_ql_user",    columnList = "user_id"),
        @Index(name = "idx_ql_created", columnList = "created_at")
    })
@Data
public class QueryLineage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", length = 64)
    private String userId;

    @Column(name = "query_text", length = 500)
    private String queryText;

    @Column(length = 50)
    private String intent;

    @Column(name = "sql_used", columnDefinition = "TEXT")
    private String sqlUsed;

    @Column(name = "metric_version", length = 20)
    private String metricVersion;

    @Column(name = "row_count")
    private Integer rowCount;

    @Column(name = "runtime_ms")
    private Long runtimeMs;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
