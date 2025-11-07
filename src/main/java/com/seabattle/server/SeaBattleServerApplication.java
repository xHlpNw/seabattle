package com.seabattle.server;

import com.seabattle.server.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class SeaBattleServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(SeaBattleServerApplication.class, args);
	}

    @Bean
    CommandLineRunner run(UserRepository userRepository) {
        return args -> {
            System.out.println("Connected to database!");
            System.out.println("Users count: " + userRepository.count());
        };
    }

}
