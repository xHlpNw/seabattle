package com.seabattle.server.util;

import com.seabattle.server.service.MyUserDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtUtil jwtUtil;
    private final MyUserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, MyUserDetailsService uds) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = uds;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {


        final String authHeader = request.getHeader("Authorization");
        String token = null;
        String username = null;
        String authFailReason = null;

        if (authHeader == null) {
            authFailReason = "no Authorization header";
        } else if (!authHeader.startsWith("Bearer ")) {
            authFailReason = "Authorization header without 'Bearer ' prefix";
        } else {
            token = authHeader.substring(7);
            if (jwtUtil.validateToken(token)) {
                username = jwtUtil.extractUsername(token);
            } else {
                authFailReason = "token rejected by validateToken (expired / bad signature / malformed)";
            }
        }

        // Log only for protected endpoints (skip noisy public ones).
        String path = request.getRequestURI();
        boolean isPublicPath = path.startsWith("/api/users/login")
                || path.startsWith("/api/users/register")
                || path.startsWith("/api/users/top")
                || path.startsWith("/api/users/profile")
                || path.startsWith("/api/avatars/")
                || path.startsWith("/api/ws/");
        if (username != null) {
            log.debug("JWT auth OK for {} on {}", username, path);
        } else if (!isPublicPath) {
            log.warn("JWT auth failed on {} ({} {}): {}",
                    path, request.getMethod(), request.getRemoteAddr(), authFailReason);
        }

        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

            // Важно: пометить как authenticated
            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authToken);

            log.debug("Authentication set for user: {}", username);
        }

        filterChain.doFilter(request, response);
    }
}
