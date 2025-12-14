package com.seabattle.server.repository;

import com.seabattle.server.entity.Game;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface GameRepository extends JpaRepository<Game, UUID> {
    java.util.List<Game> findByHostIdAndTypeAndStatus(UUID hostId, Game.GameType type, Game.GameStatus status);
    java.util.List<Game> findByRoomToken(UUID roomToken);
}