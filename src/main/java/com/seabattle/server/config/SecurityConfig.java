package com.seabattle.server.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seabattle.server.service.MyUserDetailsService;
import com.seabattle.server.util.JwtAuthenticationFilter;
import com.seabattle.server.util.JwtUtil;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.Map;

@Configuration
public class SecurityConfig {

    private final MyUserDetailsService userDetailsService;
    private final JwtUtil jwtUtil;

    public SecurityConfig(MyUserDetailsService uds, JwtUtil jwtUtil) {
        this.userDetailsService = uds;
        this.jwtUtil = jwtUtil;
    }

    @Bean
    public SecurityFilterChain filterChain(org.springframework.security.config.annotation.web.builders.HttpSecurity http) throws Exception {
        JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(jwtUtil, userDetailsService);

        http
                .cors(Customizer.withDefaults())
                .csrf(cs -> cs.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(eh -> eh.authenticationEntryPoint(jsonAuthEntryPoint()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/",
                                "/index.html",
                                "/favicon.ico",
                                "/assets/**",
                                "/*.js", "/*.css", "/*.ico",
                                "/login", "/register", "/lobby", "/lobby/**", "/profile", "/setup", "/game", "/game/**",
                                "/api/users/register",
                                "/api/users/login",
                                "/default_avatar.png",
                                "/api/avatars/**",
                                "/images/**",
                                "/css/**",
                                "/js/**",
                                "/api/users/top",
                                "/api/users/profile",
                                "/api/ws/**"
                        ).permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Returns 401 with a JSON body when the JWT is missing/invalid/expired,
     * instead of Spring Security's default opaque 403. Lets the frontend
     * distinguish "not authenticated" from "forbidden" and react accordingly
     * (clear stale token, redirect to login).
     */
    @Bean
    public AuthenticationEntryPoint jsonAuthEntryPoint() {
        ObjectMapper mapper = new ObjectMapper();
        return (request, response, authException) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            mapper.writeValue(response.getOutputStream(),
                    Map.of("message", "Authentication required",
                           "reason", authException.getMessage() != null ? authException.getMessage() : "unauthorized"));
        };
    }

}
