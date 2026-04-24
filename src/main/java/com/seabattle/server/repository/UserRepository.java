package com.seabattle.server.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import com.seabattle.server.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    Page<User> findByUsernameContainingIgnoreCase(String username, Pageable pageable);

    @Query("""
           select u from User u
           where (:q is null or :q = '' or lower(u.username) like lower(concat('%', :q, '%')))
             and (:role is null or u.role = :role)
             and (:status is null or u.status = :status)
           """)
    Page<User> search(@Param("q") String q,
                      @Param("role") User.Role role,
                      @Param("status") User.Status status,
                      Pageable pageable);
}
