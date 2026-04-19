package com.seabattle.server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
public class WebConfig {

    @Value("${app.cors.allowed-origins:*}")
    private String allowedOrigins;

    @Value("${app.upload-dir:uploads}")
    private String uploadDir;

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                CorsRegistration registration = registry.addMapping("/**")
                        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                        .allowedHeaders("*");
                if ("*".equals(allowedOrigins.trim())) {
                    registration.allowedOrigins("*").allowCredentials(false);
                } else {
                    registration.allowedOrigins(allowedOrigins.trim().split("\\s*,\\s*")).allowCredentials(true);
                }
            }

            @Override
            public void addResourceHandlers(ResourceHandlerRegistry registry) {
                String avatarPath = Paths.get(uploadDir, "avatars").toAbsolutePath().toUri().toString();
                registry.addResourceHandler("/avatars/**")
                        .addResourceLocations(avatarPath);
            }
        };
    }
}