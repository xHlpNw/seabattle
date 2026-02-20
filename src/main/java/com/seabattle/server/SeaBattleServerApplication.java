package com.seabattle.server;

import com.seabattle.server.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class SeaBattleServerApplication {

    private static final Logger log = LoggerFactory.getLogger(SeaBattleServerApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(SeaBattleServerApplication.class, args);
	}

    @Bean
    CommandLineRunner run(UserRepository userRepository) {
        return args -> {
            log.info("Connected to database. Users count: {}", userRepository.count());
        };
    }

}
