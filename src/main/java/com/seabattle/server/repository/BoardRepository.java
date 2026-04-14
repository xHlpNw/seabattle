package com.seabattle.server.repository;

import com.seabattle.server.entity.Board;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BoardRepository extends JpaRepository<Board, UUID> {
    Optional<Board> findByGameIdAndPlayerId(UUID gameId, UUID playerId);

    /** Use when at most one board per (game, player) is expected; returns first if duplicates exist (e.g. legacy data). */
    Optional<Board> findFirstByGameIdAndPlayerIdOrderByIdAsc(UUID gameId, UUID playerId);

    Optional<Board> findByGameIdAndPlayerIsNull(UUID gameId);

    Optional<Board> findByGameIdAndPlayerIdNot(UUID gameId, UUID playerId);

    Optional<Board> findByGameIdAndPlayerIdIsNull(UUID gameId);

    Optional<Board> findByGameIdAndPlayerIdIsNotNull(UUID gameId);
}
