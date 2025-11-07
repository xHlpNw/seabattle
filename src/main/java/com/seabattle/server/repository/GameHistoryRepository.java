package com.seabattle.server.repository;

import com.seabattle.server.entity.GameHistory;
import com.seabattle.server.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface GameHistoryRepository extends JpaRepository<GameHistory, UUID> {
    @Query("SELECT g FROM GameHistory g WHERE g.player = :user OR g.opponent = :user")
    List<GameHistory> findAllByUser(@Param("user") User user);

    List<GameHistory> findByPlayer(User player);

    List<GameHistory> findByOpponent(User opponent);
}
