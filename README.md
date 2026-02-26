# Sea Battle (Морской бой)

Веб-игра «Морской бой»: один игрок против бота и мультиплеер по сети. 

---

## Возможности

- **Игра с ботом** — умный бот с прицеливанием по вероятностям
- **Мультиплеер** — комнаты по ссылке, игра вдвоём
- **Рейтинг** — таблица лидеров
- **Расстановка кораблей** — вручную или авто

---

## Технологии

### Бэкенд

| Технология | Назначение |
|------------|------------|
| **Java 17** | Язык |
| **Spring Boot 4.0.2** | Каркас приложения |
| **PostgreSQL 13+** | База данных (проверялось на 17.8) |
| **Flyway 12.0.2** | Миграции схемы БД |
| **JJWT 0.11.5** | Создание и проверка JWT |

### Фронтенд

| Технология | Назначение |
|------------|------------|
| **Angular 20.3** | SPA-фреймворк |
| **TypeScript 5.9** | Язык |
| **RxJS 7.8** | Реактивные потоки |
| **SCSS** | Стили |
| **Zone.js** | Зоны для Angular |

### Инфраструктура / инструменты

- **Maven** — сборка бэкенда (Maven Wrapper в репозитории)
- **npm** — зависимости и сборка фронтенда
- **Node.js 18+** — для Angular CLI и сборки

---

## Версии технологий

| Компонент | Версия |
|-----------|--------|
| Java | **17** |
| Spring Boot | **4.0.2** |
| PostgreSQL | 13+ (проверялось на **17.8**) |
| Flyway | 12.0.2 |
| Angular | 20.3.x |
| TypeScript | 5.9.x |
| Node.js | 18+ (для сборки фронта) |

---

## Требования

- **Java 17** (указано в `pom.xml`)
- **Node.js 18+** и **npm** — для сборки и запуска фронтенда
- **PostgreSQL 13+** (проверялось на 17.8)
- **Maven 3.6+** — или использовать `mvnw` / `mvnw.cmd` из папки `server`

---

## Подготовка БД

Подключись к PostgreSQL под суперпользователем и выполни:

```sql
CREATE DATABASE battleship_db;
CREATE USER battleship_user WITH PASSWORD 'battleship_pass';
GRANT ALL PRIVILEGES ON DATABASE battleship_db TO battleship_user;

\c battleship_db
GRANT USAGE, CREATE ON SCHEMA public TO battleship_user;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO battleship_user;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO battleship_user;
```

Схема создаётся при первом запуске приложения через **Flyway** (миграции в `src/main/resources/db/migration/`).

По умолчанию в `application.yaml` указан порт PostgreSQL **5433**. Если у тебя БД на стандартном порту 5432, измени в `application.yaml` строку `spring.datasource.url` на `jdbc:postgresql://localhost:5432/battleship_db`.

---

## Запуск (локально)

### 1. Бэкенд

Из корня репозитория перейди в папку `server` и запусти приложение:

**Windows (cmd или PowerShell):**
```cmd
cd server
mvnw.cmd spring-boot:run
```

**Linux / macOS:**
```bash
cd server
./mvnw spring-boot:run
```

После старта бэкенд доступен по адресу **http://localhost:8080**.

### 2. Фронтенд

В **другом** терминале:

```bash
cd server/frontend
npm install
npm start
```

В браузере открой **http://localhost:4200**.

Запросы с фронта к API проксируются на `localhost:8080` (настройка в `proxy.network.conf.json`). Для доступа с другого устройства в сети при необходимости измени в этом файле IP/порт бэкенда.


## Запуск на хосте (production)

На сервере нужны: **Java 17**, PostgreSQL, переменные окружения и (по желанию) reverse proxy (Nginx) и systemd.

### 1. Переменные окружения

Обязательные (без них приложение с профилем `prod` не стартует):

| Переменная | Описание |
|------------|----------|
| `SPRING_DATASOURCE_URL` | JDBC URL, например `jdbc:postgresql://localhost:5432/battleship_db` (порт 5432 или 5433 — по твоей установке PostgreSQL) |
| `SPRING_DATASOURCE_USERNAME` | Пользователь БД |
| `SPRING_DATASOURCE_PASSWORD` | Пароль БД |
| `JWT_SECRET` | Секрет для подписи JWT (длинная случайная строка) |

Опциональные:

| Переменная | Описание |
|------------|----------|
| `JWT_EXPIRATION_MS` | Время жизни токена в мс (по умолчанию 3600000) |
| `CORS_ALLOWED_ORIGINS` | Разрешённые origin для CORS и WebSocket через запятую, например `https://mygame.com` или `https://mygame.com,https://www.mygame.com` |

### 2. Сборка и запуск JAR

На машине с установленными **Java 17** и Maven (или только Java, если JAR собираешь локально):

```bash
cd server
./mvnw clean package -DskipTests
```

JAR: `target/server-0.0.1-SNAPSHOT.jar`.

**Запуск с профилем prod:**

Linux/macOS:

```bash
export SPRING_PROFILES_ACTIVE=prod
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/battleship_db
export SPRING_DATASOURCE_USERNAME=battleship_user
export SPRING_DATASOURCE_PASSWORD=ваш_пароль
export JWT_SECRET=ваш_длинный_секрет
# при доступе с другого домена:
export CORS_ALLOWED_ORIGINS=https://your-domain.com

java -jar target/server-0.0.1-SNAPSHOT.jar
```

Windows (cmd):

```cmd
set SPRING_PROFILES_ACTIVE=prod
set SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/battleship_db
set SPRING_DATASOURCE_USERNAME=battleship_user
set SPRING_DATASOURCE_PASSWORD=ваш_пароль
set JWT_SECRET=ваш_длинный_секрет
set CORS_ALLOWED_ORIGINS=https://your-domain.com

java -jar target\server-0.0.1-SNAPSHOT.jar
```

Приложение слушает порт **8080** (значение по умолчанию в `application.yaml`).

### 3. Фронтенд в production: сборка, домен и раздача

В production-сборке фронт не прописывает адрес бэкенда в коде: запросы идут на **тот же домен и порт**, с которого открыта страница (относительные URL `/api/...` и WebSocket на том же origin). Поэтому важно, **как** и **под каким адресом** раздаёшь фронт.

---

#### Как собирается фронт для прода

На машине с Node.js 18+:

```bash
cd server/frontend
npm install
npm run build
```

- Сборка идёт в **production**-режиме (минификация, без лишних карт и т.п.).
- Результат лежит в **`frontend/dist/frontend-app/`**: там `index.html`, `main-*.js`, `polyfills-*.js`, `styles-*.css` и т.д.
- В этой сборке подставляется `environment.prod.ts`: `apiBaseUrl: ''`, то есть все запросы к API и WebSocket идут на тот же хост, с которого открыт сайт.

Никакого отдельного «домена» в коде фронта задавать не нужно: домен определяется тем, по какому URL пользователь открывает приложение (например `https://seabattle.example.com`).

---

#### Вариант A: Один домен (фронт и API на одном адресе) — рекомендуется

Пользователь заходит на один и тот же домен, например **https://seabattle.example.com**. Статику (фронт) раздаёт веб-сервер, а запросы к `/api` и WebSocket он проксирует на бэкенд.

**Шаги:**

1. Собрать фронт (команды выше). Скопировать **всё содержимое** папки `frontend/dist/frontend-app/` на сервер (в каталог, из которого Nginx будет отдавать статику, например `/var/www/seabattle/`).

2. Запустить бэкенд с профилем `prod` на том же сервере (например на порту 8080). Переменную **`CORS_ALLOWED_ORIGINS` задавать не нужно**: браузер обращается к `https://seabattle.example.com`, запросы к API тоже идут на `https://seabattle.example.com/api/...` — это один origin, CORS не участвует.

3. Настроить Nginx (или аналог) так, чтобы:
   - по **https://seabattle.example.com/** отдавались файлы из `/var/www/seabattle/` (index.html, js, css);
   - запросы к **https://seabattle.example.com/api/** проксировались на `http://127.0.0.1:8080/api/`;
   - WebSocket **https://seabattle.example.com/api/ws/** проксировался на `http://127.0.0.1:8080/api/ws/`;
   - для SPA: если по пути нет файла (например `/lobby`, `/game/123`), отдавать `index.html`.

**Пример конфига Nginx** (замени `seabattle.example.com` на свой домен и пути при необходимости):

```nginx
server {
    listen 443 ssl http2;
    server_name seabattle.example.com;

    ssl_certificate     /etc/ssl/certs/seabattle.example.com.crt;
    ssl_certificate_key /etc/ssl/private/seabattle.example.com.key;

    root /var/www/seabattle;
    index index.html;

    # Статика фронта
    location / {
        try_files $uri $uri/ /index.html;
    }

    # REST API — прокси на бэкенд
    location /api/ {
        proxy_pass http://127.0.0.1:8080/api/;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # WebSocket — прокси на бэкенд
    location /api/ws/ {
        proxy_pass http://127.0.0.1:8080/api/ws/;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

После перезагрузки Nginx пользователи заходят на **https://seabattle.example.com** — это и есть «заданный домен»; отдельно в коде фронта его прописывать не нужно.

---

#### Вариант B: Фронт и бэкенд на разных доменах

Если фронт открывается, например, с **https://game.mycompany.com**, а бэкенд — на **https://api.mycompany.com**, то браузер считает это разными origin. Тогда:

1. На бэкенде **обязательно** задать переменную окружения **`CORS_ALLOWED_ORIGINS`** — точный адрес, с которого открывается фронт (без слэша в конце):

   ```bash
   export CORS_ALLOWED_ORIGINS=https://game.mycompany.com
   ```

   Несколько доменов через запятую:

   ```bash
   export CORS_ALLOWED_ORIGINS=https://game.mycompany.com,https://www.mycompany.com
   ```

2. Во фронте для production при таком сценарии нужно, чтобы запросы шли на `https://api.mycompany.com`. Сейчас в `environment.prod.ts` стоит `apiBaseUrl: ''` (тот же origin). Для разнесённых доменов пришлось бы завести отдельный конфиг (например `environment.prod-api-host.ts`) и при сборке подставлять его, либо задавать API URL через сборку/переменные окружения при сборке — это уже кастомная настройка. Рекомендуемый и простой вариант для прода — **вариант A (один домен)**.

---

#### Вариант C: Раздача фронта самим бэкендом (один JAR, один порт)

Фронт можно не раздавать через Nginx, а отдавать из Spring Boot:

1. Собрать фронт: `npm run build`.
2. Скопировать всё из `frontend/dist/frontend-app/` в **`server/src/main/resources/static/`** (скрипт `scripts/copy-frontend.cmd` / `scripts/copy-frontend.ps1`).
3. Пересобрать JAR и запустить только бэкенд с профилем `prod`.

Пользователь заходит на **https://your-server:8080** (или за Nginx на **https://seabattle.example.com**, если проксируешь весь трафик на 8080). Домен здесь — тот, по которому пользователь обращается к приложению; `CORS_ALLOWED_ORIGINS` при одном домене для фронта и API не нужен.

---

#### Кратко: как «задать домен»

- **Домен** — это просто адрес, по которому пользователи открывают игру в браузере (например `https://seabattle.example.com`).
- Задаётся он не в коде, а тем, как настроен веб-сервер (Nginx) и DNS: на какой `server_name` и с каким SSL-сертификатом отвечает Nginx, и что скопировано в `root` (каталог со сборкой фронта).
- Если фронт и API доступны по **одному и тому же** домену (вариант A или C) — больше ничего настраивать не нужно. Если фронт на одном домене, а API на другом — нужно задать **`CORS_ALLOWED_ORIGINS`** на бэкенде равным URL фронта (например `https://game.mycompany.com`).

### 4. Пример unit для systemd (Linux)

Файл `/etc/systemd/system/seabattle.service`:

```ini
[Unit]
Description=Sea Battle Backend
After=network.target postgresql.service

[Service]
Type=simple
User=seabattle
WorkingDirectory=/opt/seabattle
EnvironmentFile=/opt/seabattle/env
ExecStart=/usr/bin/java -jar /opt/seabattle/server-0.0.1-SNAPSHOT.jar
Restart=on-failure

[Install]
WantedBy=multi-user.target
```

В `/opt/seabattle/env` задай переменные: `SPRING_PROFILES_ACTIVE=prod`, `SPRING_DATASOURCE_*`, `JWT_SECRET`, при необходимости `CORS_ALLOWED_ORIGINS`.

---

## Конфигурация

- **Без профиля** — используются настройки из `application.yaml` (БД localhost, JWT из файла, CORS `*`). Удобно для локального теста.
- **Профиль `dev`** — `application-dev.yaml`: логирование SQL, DEBUG для приложения.
- **Профиль `prod`** — `application-prod.yaml`: БД и JWT только из переменных окружения, настройки HikariCP, отключён Flyway clean, CORS из `CORS_ALLOWED_ORIGINS`.

Активация профиля: `-Dspring-boot.run.profiles=prod` при `spring-boot:run` или переменная `SPRING_PROFILES_ACTIVE=prod` при запуске JAR.

---

## Как играть

1. Зарегистрироваться или войти.
2. Выбрать «Игра с ботом» или «Игра онлайн» (создать комнату или перейти по ссылке).
3. Расставить корабли (вручную или авто).
4. По очереди стрелять по клеткам противника; побеждает тот, кто первым потопит все корабли.

Правила классические: поле 10×10, корабли не соприкасаются.

---

## Устранение неполадок

- **Ошибка подключения к БД** — проверь, что PostgreSQL запущен, пользователь и база созданы, права на схему `public` выданы (см. раздел «Подготовка БД»).
- **CORS / 403 при запросах с фронта** — в prod задай `CORS_ALLOWED_ORIGINS` (например `http://192.168.1.121:4200` для теста по сети или `https://your-domain.com` для продакшена).
- **«Нет доступа к схеме public»** — выполни для пользователя БД: `GRANT USAGE, CREATE ON SCHEMA public TO battleship_user;` и при необходимости права на таблицы и последовательности.
- **Порт 8080 занят** — смени порт в `application.yaml` (`server.port`) или задай `SERVER_PORT` в окружении.

---

## Структура проекта

```
server/
├── src/main/java/com/seabattle/server/
│   ├── config/          # Security, CORS, WebSocket
│   ├── controller/      # REST и SPA fallback
│   ├── dto/
│   ├── entity/
│   ├── engine/          # Логика поля и ИИ бота
│   ├── repository/
│   ├── service/
│   └── util/            # JWT
├── src/main/resources/
│   ├── application.yaml
│   ├── application-dev.yaml
│   ├── application-prod.yaml
│   └── db/migration/    # Flyway
├── frontend/            # Angular SPA
│   ├── src/app/
│   │   ├── core/        # API, auth, WebSocket
│   │   ├── pages/
│   │   └── environments/
│   └── proxy.conf.json / proxy.network.conf.json
├── scripts/             # copy-frontend.cmd, .ps1
└── README.md
```
