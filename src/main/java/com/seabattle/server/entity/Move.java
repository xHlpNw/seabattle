package com.seabattle.server.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "moves")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Move {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;

    @ManyToOne
    @JoinColumn(name = "player_id")
    private User player;

    @Column(nullable = false)
    private short x;

    @Column(nullable = false)
    private short y;

    @Column(nullable = false)
    private boolean hit;

    @Column(name = "created_at")
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
