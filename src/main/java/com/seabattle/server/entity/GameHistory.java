package com.seabattle.server.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "game_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;

    @ManyToOne
    @JoinColumn(name = "player_id", nullable = false)
    private User player;

    @ManyToOne
    @JoinColumn(name = "opponent_id")
    private User opponent;

    @Column(nullable = false, length = 10)
    private String result; // WIN / LOSS / DRAW

    @Column(name = "delta_rating")
    private Integer deltaRating = 0;

    @Column(name = "created_at")
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
