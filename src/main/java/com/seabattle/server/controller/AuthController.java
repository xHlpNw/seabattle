package com.seabattle.server.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /**
     * Проверка валидности токена. Вызывается с заголовком Authorization: Bearer &lt;token&gt;.
     * Возвращает 200, если токен валиден и пользователь существует в БД; иначе Spring Security вернёт 401.
     */
    @GetMapping("/validate")
    public ResponseEntity<Map<String, Boolean>> validate() {
        return ResponseEntity.ok(Map.of("valid", true));
    }
}
