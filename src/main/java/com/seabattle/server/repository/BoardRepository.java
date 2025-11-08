package com.seabattle.server.repository;

import com.seabattle.server.entity.Board;
import com.seabattle.server.entity.Game;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BoardRepository extends JpaRepository<Board, UUID> {
    Optional<Board> findByGameIdAndPlayerId(UUID gameId, UUID playerId);

    Optional<Board> findByGameIdAndPlayerIsNull(UUID gameId);
}
