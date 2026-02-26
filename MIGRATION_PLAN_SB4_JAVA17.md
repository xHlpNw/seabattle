# План перехода на Spring Boot 4.x + Java 17

Проект: **Sea Battle Server**  
Текущее состояние: Java 21, Spring Boot 3.5.7  
Цель: Java 17, Spring Boot 4.0.x

---

## Фаза 0. Подготовка

- [x] Убедиться, что локально установлена **JDK 17** (`java -version`).
- [ ] Создать отдельную ветку для миграции, например: `git checkout -b migration/sb4-java17`.
- [ ] Убедиться, что текущая сборка и тесты проходят:  
  `mvnw.cmd clean test` (из каталога `server`).
- [ ] Сохранить резервную копию `pom.xml` или зафиксировать текущее состояние в git.

**Справочник:** [Spring Boot 4.0 Migration Guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)

---

## Фаза 1. Обновление до последнего 3.5.x (рекомендуется)

Перед переходом на 4.x официально рекомендуется обновиться до последней версии линейки 3.5.x и убрать использование устаревших API.

- [ ] В `pom.xml` обновить родительский Spring Boot до последнего 3.5.x (например `3.5.8` или актуального по [releases](https://github.com/spring-projects/spring-boot/releases)).
- [ ] Запустить сборку и тесты. Исправить предупреждения об устаревших методах (deprecation), если появятся.
- [ ] Закоммитить изменения.

---

## Фаза 2. Java 17 и Spring Boot 4 в POM

### 2.1. Версии

- [x] В `<properties>` выставить:
  ```xml
  <java.version>17</java.version>
  ```
- [x] В `<parent>` заменить версию Spring Boot на 4.0.x (например `4.0.2`):
  ```xml
  <version>4.0.2</version>
  ```

### 2.2. Замена и добавление стартеров (модульная структура SB 4)

В Spring Boot 4 ряд стартеров переименован или разбит на модули. Для быстрого перехода можно использовать **classic-стартеры**, затем при желании перейти на новые.

**Вариант A — быстрый (classic starters):**

- [ ] Заменить `spring-boot-starter-web` на:
  ```xml
  <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-webmvc</artifactId>
  </dependency>
  ```
  (или добавить `spring-boot-starter-classic` и оставить текущие стартеры — см. Migration Guide).

- [ ] Для тестов: заменить `spring-boot-starter-test` на:
  ```xml
  <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test-classic</artifactId>
      <scope>test</scope>
  </dependency>
  ```
  и при использовании `@WithMockUser` / `@WithUserDetails` добавить:
  ```xml
  <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-security-test</artifactId>
      <scope>test</scope>
  </dependency>
  ```

**Вариант B — полная модульность (по желанию, после того как вариант A заработает):**

- [ ] Использовать стартеры по таблице из Migration Guide, например:
  - `spring-boot-starter-web` → `spring-boot-starter-webmvc`
  - тесты: `spring-boot-starter-webmvc-test`, `spring-boot-starter-data-jpa-test`, `spring-boot-starter-security-test`, `spring-boot-starter-websocket-test` и т.д.

### 2.3. Flyway (в проекте уже есть)

В SB 4 для Flyway нужен стартер:

- [ ] Удалить:
  ```xml
  <dependency>
      <groupId>org.flywaydb</groupId>
      <artifactId>flyway-core</artifactId>
  </dependency>
  <dependency>
      <groupId>org.flywaydb</groupId>
      <artifactId>flyway-database-postgresql</artifactId>
  </dependency>
  ```
- [ ] Добавить:
  ```xml
  <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-flyway</artifactId>
  </dependency>
  ```

### 2.4. Сборка после изменений POM

- [ ] Выполнить: `mvnw.cmd clean compile`.  
- [ ] Исправить ошибки компиляции (импорты, переименованные/удалённые классы). При использовании classic-стартеров изменений в коде обычно минимум.

---

## Фаза 3. Jackson (совместимость с Jackson 2)

В проекте используется `com.fasterxml.jackson.databind.ObjectMapper` в:

- `BoardModel.java`
- `GameWebSocketHandler.java`
- `RoomWebSocketHandler.java`
- `GameController.java`
- тестах (например, `BotGamePrintBoardTest.java`)

Spring Boot 4 по умолчанию переходит на Jackson 3. Чтобы не менять код сразу:

- [ ] В `src/main/resources/application.yaml` добавить (в корень или под `spring:`):
  ```yaml
  spring:
    jackson:
      use-jackson2-defaults: true
  ```
- [ ] Пересобрать и запустить приложение. Убедиться, что REST и WebSocket по-прежнему сериализуют/десериализуют JSON корректно.

Опционально позже: миграция на Jackson 3 (новые groupId `tools.jackson`, пакеты и переименованные классы — см. Migration Guide, раздел "Upgrading Jackson").

---

## Фаза 4. Конфигурация и поведение

- [ ] **DevTools:** если нужен live reload, в `application.yaml` добавить:
  ```yaml
  spring:
    devtools:
      livereload:
        enabled: true
  ```
  (в SB 4 по умолчанию отключён.)

- [ ] **Логирование:** при необходимости проверить кодировку логов (по умолчанию в SB 4 — UTF-8).

- [ ] **Jakarta EE 11 / Servlet 6.1:** импорты `jakarta.servlet.*` и `jakarta.persistence.*` обычно совместимы. При появлении ошибок — свериться с [Spring Framework 7.0 Release Notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-7.0-Release-Notes) и [Spring Security 7.0 Migration](https://docs.spring.io/spring-security/reference/7.0/migration/).

---

## Фаза 5. Сторонние зависимости

- [ ] **JJWT (0.11.5):** проверить совместимость с Spring Boot 4 и Spring Security 7. При конфликтах обновить до актуальной версии jjwt (проверить [releases](https://github.com/jwtk/jjwt/releases)).
- [ ] Если в проект позже вернётся **springdoc-openapi:** подобрать версию, совместимую со Spring Boot 4 (часто требуется обновление до последней 2.x или отдельный starter для SB 4).

---

## Фаза 6. Проверка

- [ ] Сборка: `mvnw.cmd clean package -DskipTests`
- [ ] Тесты: `mvnw.cmd test`
- [ ] Запуск: `mvnw.cmd spring-boot:run`
- [ ] Ручная проверка:
  - регистрация / логин (JWT);
  - создание игры с ботом, расстановка кораблей, выстрелы;
  - при наличии — мультиплеер (лобби, WebSocket).
- [ ] При наличии фронтенда: убедиться, что прокси на backend и запросы к API работают без ошибок.

---

## Фаза 7. Финализация

- [ ] Удалить временные настройки или комментарии, оставленные для отладки миграции.
- [ ] Обновить `README.md` (и при необходимости `HELP.md`): указать Java 17 и Spring Boot 4.0.x.
- [ ] Закоммитить все изменения с сообщением вида: `chore: migrate to Spring Boot 4.x and Java 17`.

---

## Чек-лист изменений в файлах

| Файл / место | Действие |
|--------------|----------|
| `pom.xml` | Java 17, SB 4.0.x, web→webmvc (или classic), Flyway→starter-flyway, тестовые стартеры |
| `application.yaml` | `spring.jackson.use-jackson2-defaults: true`, при необходимости `spring.devtools.livereload.enabled: true` |
| Код (Jackson) | Без изменений при использовании Jackson 2 compatibility |
| Код (Jakarta) | Точечные правки только при появлении ошибок компиляции/рантайма |

---

## Откат

При серьёзных проблемах:

- восстановить `pom.xml` и `application.yaml` из git;
- переключиться обратно на ветку с Java 21 и Spring Boot 3.5.x.

---

## Полезные ссылки

- [Spring Boot 4.0 Migration Guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)
- [Spring Boot 4.0 System Requirements](https://docs.spring.io/spring-boot/docs/4.0.x/reference/html/system-requirements.html)
- [Spring Framework 7.0 Release Notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-7.0-Release-Notes)
- [Spring Security 7.0 Migration](https://docs.spring.io/spring-security/reference/7.0/migration/)
