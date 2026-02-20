# Battleship Game (Sea Battle)

A modern web-based implementation of the classic Battleship board game featuring both single-player (vs AI bot) and multiplayer modes.

##  Features

- **Single Player Mode**: Play against an intelligent AI bot with advanced targeting algorithms
- **Multiplayer Mode**: Challenge other players online (infrastructure ready)
- **User Authentication**: JWT-based login and registration system
- **Rating System**: ELO-based competitive rankings
- **Real-time Gameplay**: WebSocket support for live updates
- **Ship Auto-placement**: Manual or automatic ship placement options
- **Responsive Design**: Works on desktop and mobile devices

## Technology Stack

### Backend
- **Java 21**
- **Spring Boot 3.5.7**
- **PostgreSQL** database
- **Spring Security** with JWT authentication
- **WebSocket** for real-time communication
- **Spring Data JPA** for ORM

### Frontend
- **Angular 20.3.0**
- **TypeScript**
- **RxJS** for reactive programming
- **SCSS** for styling

## Prerequisites

Before running the application, ensure you have the following installed:

1. **Java 21** or higher
   ```bash
   java -version
   ```

2. **Node.js 18+** and npm
   ```bash
   node --version
   npm --version
   ```

3. **PostgreSQL 13+**
   ```bash
   psql --version
   ```

4. **Maven** (usually comes with Java development kits)
   ```bash
   mvn --version
   ```

## Quick Start

### 1. Clone the Repository

```bash
git clone <repository-url>
cd seabattle
```

### 2. Database Setup

Create PostgreSQL database and user:

```sql
-- Create database
CREATE DATABASE battleship_db;

-- Create user
CREATE USER battleship_user WITH PASSWORD 'battleship_pass';

-- Grant database privileges
GRANT ALL PRIVILEGES ON DATABASE battleship_db TO battleship_user;

-- Connect to battleship_db (e.g. \c battleship_db in psql), then grant schema access
-- Required in PostgreSQL 15+ so the user can create tables in public schema
\c battleship_db
GRANT USAGE, CREATE ON SCHEMA public TO battleship_user;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO battleship_user;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO battleship_user;
```

**Note**: The application uses **Flyway** to create and update the database schema (migrations in `src/main/resources/db/migration/`). Hibernate is set to `validate` only. On first run, Flyway will create all tables. If you had an existing database created by Hibernate earlier, either use a fresh database or see [Flyway baseline](https://flywaydb.org/documentation/usage/commandline/baseline) for existing databases.

### 3. Backend Setup

Navigate to the server directory:

```bash
cd server
```

The backend is already configured to connect to:
- **Database**: `battleship_db` on `localhost:5432`
- **Username**: `battleship_user`
- **Password**: `battleship_pass`

Start the Spring Boot server (optionally with profile `dev` for SQL logging):

```bash
./mvnw spring-boot:run
# or with dev profile:
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Or on Windows:

```cmd
mvnw.cmd spring-boot:run
```

The backend will start on `http://localhost:8080`.

#### Вариант: бэкенд в IDEA + фронт из сборки (один порт)

Если запускаешь бэкенд из **IDEA**, а фронт только собираешь (`npm run build`):

1. Собрать фронт: `cd frontend && npm run build`
2. Скопировать сборку в ресурсы бэкенда:
   - Windows (из папки `server`): `scripts\copy-frontend.cmd` или `powershell -File scripts\copy-frontend.ps1`
   - Вручную: скопировать всё из `frontend/dist/frontend-app/` в `src/main/resources/static/`
3. Запустить приложение в IDEA.
4. Открыть в браузере: **http://localhost:8080**

Приложение и API работают с одного адреса.

### 4. Frontend Setup (режим разработки)

Open a new terminal and navigate to the frontend directory:

```bash
cd server/frontend
```

Install dependencies:

```bash
npm install
```

Start the Angular development server:

```bash
npm start
```

The frontend will start on `http://localhost:4200` with proxy configuration to connect to the backend.

### 5. Access the Application

Open your browser and navigate to: **http://localhost:4200**

## How to Play

### Getting Started
1. **Register** a new account or **Login** with existing credentials
2. Choose your game mode:
   - **Play with Bot**: Single-player against AI
   - **Play Online**: Multiplayer (when available)

### Game Rules
- **Objective**: Be the first to sink all enemy ships
- **Grid**: 10x10 battlefield
- **Ships**: Standard naval fleet (Carrier, Battleship, Cruiser, Destroyers, Submarines)
- **Turns**: Players alternate attacking coordinates
- **Hits & Misses**: Red X for hits, white dot for misses

### Ship Placement
- **Manual**: Click and drag ships onto your grid
- **Auto-place**: Let the system randomly place your fleet
- Ships cannot touch each other (not even diagonally)

### AI Bot Features
- **Intelligent Targeting**: Uses probability maps and pattern recognition
- **Adaptive Strategy**: Switches between random and focused hunting modes
- **Ship Detection**: Identifies ship orientation and targets accordingly

## Configuration

### Backend Configuration

Key settings in `server/src/main/resources/application.yaml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/battleship_db
    username: battleship_user
    password: battleship_pass

jwt:
  secret: YOUR_SECRET_KEY_HERE
  expiration-ms: 3600000
```

- **Profiles**: `application-dev.yaml` (SQL logging, DEBUG) and `application-prod.yaml` (HikariCP tuning, no SQL in logs). Activate with `--spring.profiles.active=dev` or `prod`.
- **Schema**: Managed by **Flyway** (`db/migration/`). Hibernate uses `ddl-auto: validate` only.

### Frontend Configuration

- **Environments**: `src/environments/environment.ts` (dev) and `environment.prod.ts` (prod). Dev uses `http://localhost:8080` (or same host with port 8080 when testing from network). Prod uses same origin (`apiBaseUrl: ''`) — deploy frontend and reverse-proxy `/api` and WebSocket to the backend.
- **Production build**: `npm run build` (uses `environment.prod.ts` via file replacement). No dev proxy; all requests go to relative `/api` and same-origin WebSocket.

Proxy configuration for **development** only (`server/frontend/proxy.conf.json`):

```json
{
  "/api": {
    "target": "http://localhost:8080",
    "secure": false,
    "changeOrigin": true,
    "logLevel": "debug"
  }
}
```

## Production

With profile `prod` (`--spring.profiles.active=prod`) **обязательно** задать переменные окружения (в приложении нет дефолтов):

| Переменная | Описание |
|------------|----------|
| `SPRING_DATASOURCE_URL` | JDBC URL, например `jdbc:postgresql://host:5432/battleship_db` |
| `SPRING_DATASOURCE_USERNAME` | Пользователь БД |
| `SPRING_DATASOURCE_PASSWORD` | Пароль БД |
| `JWT_SECRET` | Секрет для подписи JWT (достаточно длинная строка) |
| `JWT_EXPIRATION_MS` | (необязательно) Время жизни токена в мс, по умолчанию 3600000 (1 ч) |
| `CORS_ALLOWED_ORIGINS` | (необязательно) Разрешённые origin для CORS и WebSocket, через запятую. По умолчанию в prod: `https://yourdomain.com` — замени на домен фронта (например `https://myapp.com`) |

Пример запуска:
```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/battleship_db
export SPRING_DATASOURCE_USERNAME=battleship_user
export SPRING_DATASOURCE_PASSWORD=your_secure_password
export JWT_SECRET=your_long_random_secret_key
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod
```

## Testing

### Backend Tests

```bash
cd server
./mvnw test
```

### Frontend Tests

```bash
cd server/frontend
npm test
```

## Troubleshooting

### Common Issues

1. **Database Connection Error**
   - Ensure PostgreSQL is running
   - Verify database credentials in `application.yaml`
   - Check if the database and user exist

2. **Port Already in Use**
   - Backend: Change port in `application.yaml`
   - Frontend: Change port with `ng serve --port 4201`

3. **CORS Errors**
   - Ensure frontend proxy is configured correctly
   - Check that backend allows `http://localhost:4200` origin

4. **Build Errors**
   - Clear Maven cache: `mvn clean`
   - Clear npm cache: `npm cache clean --force`
   - Reinstall dependencies

5. **Flyway: "нет доступа к схеме public" / "permission denied for schema public"**
   - PostgreSQL 15+ does not grant create on `public` to new users. Connect as superuser (e.g. `psql -U postgres -d battleship_db`) and run:
   - `GRANT USAGE, CREATE ON SCHEMA public TO battleship_user;`
   - `GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO battleship_user;`
   - `GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO battleship_user;`

### Development Mode

For development with hot reload:
- Backend: Changes are automatically picked up
- Frontend: Angular dev server provides hot module replacement

## Project Structure

```
seabattle/
├── server/                          # Backend (Spring Boot)
│   ├── src/main/java/com/seabattle/server/
│   │   ├── config/                  # Security, WebSocket configs
│   │   ├── controller/              # REST API endpoints
│   │   ├── dto/                     # Data transfer objects
│   │   ├── entity/                  # JPA entities
│   │   ├── repository/              # Data access layer
│   │   ├── service/                 # Business logic
│   │   └── engine/                  # Game logic & AI
│   ├── src/main/resources/
│   │   ├── application.yaml         # App configuration
│   │   └── data.sql                 # Initial data
│   └── frontend/                    # Frontend (Angular)
│       ├── src/app/
│       │   ├── core/                # Services & interceptors
│       │   ├── features/            # Game state management
│       │   ├── pages/               # Route components
│       │   ├── overlays/            # Modals
│       │   └── shared/              # Shared components & models
│       └── proxy.conf.json          # Dev proxy config
└── README.md                        # This file
```

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests if applicable
5. Submit a pull request

## License

This project is for educational purposes.

---

**Happy Sailing! ⚓**
