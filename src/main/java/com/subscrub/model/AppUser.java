package com.subscrub.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
public class AppUser {

    @Id
    @Column(length = 64)
    private String id;

    @Column(length = 100)
    private String name;

    @Column(length = 200)
    private String email;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    @Column(name = "upload_count")
    private int uploadCount = 0;
}
