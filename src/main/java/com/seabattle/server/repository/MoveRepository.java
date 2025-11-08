package com.seabattle.server.repository;

import com.seabattle.server.entity.Game;
import com.seabattle.server.entity.Move;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MoveRepository extends JpaRepository<Move, Long> { }
