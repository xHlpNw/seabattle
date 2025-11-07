package com.seabattle.server.repository;

import com.seabattle.server.entity.Game;
import com.seabattle.server.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

public interface GameRepository extends JpaRepository<Game, UUID> {

    List<Game> findByHostOrGuest(User host, User guest);

}