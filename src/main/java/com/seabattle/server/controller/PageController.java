package com.seabattle.server.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * SPA fallback: serve index.html for Angular routes when frontend is built and copied to static.
 * So opening http://localhost:8080/login (or /game, /lobby, etc.) returns the Angular app.
 */
@Controller
public class PageController {

    @GetMapping({ "/", "/login", "/register", "/lobby", "/profile", "/setup", "/game" })
    public String index() {
        return "forward:/index.html";
    }

    @GetMapping({ "/lobby/join/**", "/game/**" })
    public String indexSub() {
        return "forward:/index.html";
    }
}

