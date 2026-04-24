package com.seabattle.server;

import com.seabattle.server.entity.User;
import com.seabattle.server.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
public class SeaBattleServerApplication {

    private static final Logger log = LoggerFactory.getLogger(SeaBattleServerApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(SeaBattleServerApplication.class, args);
	}

    @Bean
    CommandLineRunner run(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${seabattle.reset-admin-password:false}") boolean resetAdminPassword) {
        return args -> {
            long count = userRepository.count();
            log.info("Connected to database. Users count: {}", count);

            userRepository.findByUsername("admin").ifPresentOrElse(
                    admin -> {
                        if (admin.getRole() != User.Role.ADMIN) {
                            admin.setRole(User.Role.ADMIN);
                        }
                        if (admin.getStatus() == null) {
                            admin.setStatus(User.Status.ACTIVE);
                        }
                        if (resetAdminPassword) {
                            admin.setPasswordHash(passwordEncoder.encode("password"));
                            log.info("Reset password for user 'admin' to 'password'");
                        }
                        userRepository.save(admin);
                    },
                    () -> {
                        User admin = User.builder()
                                .username("admin")
                                .passwordHash(passwordEncoder.encode("password"))
                                .avatar("/default_avatar.png")
                                .rating(0)
                                .wins(0)
                                .losses(0)
                                .role(User.Role.ADMIN)
                                .status(User.Status.ACTIVE)
                                .build();
                        userRepository.save(admin);
                        log.info("Created default user: admin / password");
                    }
            );
        };
    }

}
