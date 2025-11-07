package com.seabattle.server.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.*;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    public enum Role { PLAYER, ADMIN }

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(unique = true, length = 100)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    private Integer rating = 1000;
    private Integer wins = 0;
    private Integer losses = 0;

    @Column(name = "avatar", length = 255, columnDefinition = "varchar(255) default '/default_avatar.png'")
    private String avatar;

    @Column(name = "created_at")
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @OneToMany(mappedBy = "host", cascade = CascadeType.ALL)
    private List<Game> hostedGames = new ArrayList<>();

    @OneToMany(mappedBy = "guest", cascade = CascadeType.ALL)
    private List<Game> guestGames = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    private Role role;
}
