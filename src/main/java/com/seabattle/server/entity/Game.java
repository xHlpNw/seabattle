package com.seabattle.server.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "games")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Game {
    public enum GameType { BOT, ONLINE }
    public enum GameStatus { WAITING, IN_PROGRESS, FINISHED }
    public enum GameResult { HOST_WIN, GUEST_WIN, DRAW, SURRENDER }
    public enum Turn { HOST, GUEST }

    @Id
    @GeneratedValue(generator = "UUID")
    @org.hibernate.annotations.GenericGenerator(
            name = "UUID",
            strategy = "org.hibernate.id.UUIDGenerator"
    )
    @Column(updatable = false, nullable = false)
    private UUID id;


    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GameType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private GameStatus status = GameStatus.WAITING;

    @Enumerated(EnumType.STRING)
    private GameResult result;

    @ManyToOne
    @JoinColumn(name = "host_id", nullable = false)
    private User host;

    @ManyToOne
    @JoinColumn(name = "guest_id")
    private User guest;

    @Column(name = "is_bot")
    @Builder.Default
    private boolean isBot = false;

    @Column(name = "room_token")
    private UUID roomToken;

    @Column(name = "host_ready")
    @Builder.Default
    private boolean hostReady = false;

    @Column(name = "guest_ready")
    @Builder.Default
    private boolean guestReady = false;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Enumerated(EnumType.STRING)
    private Turn currentTurn;

    @PrePersist
    void prePersist() { if (createdAt == null) createdAt = OffsetDateTime.now(); }
}
