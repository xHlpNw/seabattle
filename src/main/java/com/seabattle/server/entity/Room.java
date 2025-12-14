package com.seabattle.server.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "rooms")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Room {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "host_id", nullable = false)
    private User host;

    @ManyToOne
    @JoinColumn(name = "guest_id")
    private User guest;

    @Column(nullable = false, unique = true)
    private UUID token;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "WAITING";

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @PrePersist
    void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (expiresAt == null) expiresAt = now.plusDays(1); // 24 hours for testing
    }
}
