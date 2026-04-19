package com.seabattle.server.repository;

import com.seabattle.server.entity.Room;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface RoomRepository extends JpaRepository<Room, UUID> {
    Room findByToken(UUID token);
    long countByStatus(String status);
    Page<Room> findByStatus(String status, Pageable pageable);
}
