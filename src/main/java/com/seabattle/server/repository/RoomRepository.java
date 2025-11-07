package com.seabattle.server.repository;

import com.seabattle.server.entity.Room;
import com.seabattle.server.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RoomRepository extends JpaRepository<Room, UUID> {

    // Найти все комнаты, где пользователь является хостом
    List<Room> findByHost(User host);

    // Можно добавить поиск по токену
    Room findByToken(UUID token);
}
