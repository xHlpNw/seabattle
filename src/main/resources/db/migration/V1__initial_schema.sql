-- Sea Battle initial schema
-- PostgreSQL: explicit public schema (fixes "схема для создания объектов не выбрана")

CREATE TABLE public.users (
    id              UUID PRIMARY KEY,
    username        VARCHAR(50) NOT NULL UNIQUE,
    email           VARCHAR(100) UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    rating          INTEGER DEFAULT 0,
    wins            INTEGER DEFAULT 0,
    losses          INTEGER DEFAULT 0,
    avatar          VARCHAR(255) DEFAULT '/default_avatar.png',
    created_at      TIMESTAMP WITH TIME ZONE,
    updated_at      TIMESTAMP WITH TIME ZONE,
    role            VARCHAR(20)
);

CREATE TABLE public.games (
    id           UUID PRIMARY KEY,
    type         VARCHAR(20) NOT NULL,
    status       VARCHAR(20) NOT NULL,
    result       VARCHAR(20),
    host_id      UUID NOT NULL REFERENCES public.users(id),
    guest_id     UUID REFERENCES public.users(id),
    is_bot       BOOLEAN DEFAULT FALSE,
    room_token   UUID,
    host_ready   BOOLEAN DEFAULT FALSE,
    guest_ready  BOOLEAN DEFAULT FALSE,
    started_at   TIMESTAMP WITH TIME ZONE,
    finished_at  TIMESTAMP WITH TIME ZONE,
    created_at   TIMESTAMP WITH TIME ZONE,
    current_turn VARCHAR(10)
);

CREATE TABLE public.rooms (
    id         UUID PRIMARY KEY,
    host_id    UUID NOT NULL REFERENCES public.users(id),
    guest_id   UUID REFERENCES public.users(id),
    token      UUID NOT NULL UNIQUE,
    status     VARCHAR(20) NOT NULL DEFAULT 'WAITING',
    created_at TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE public.boards (
    id         UUID PRIMARY KEY,
    game_id    UUID NOT NULL REFERENCES public.games(id),
    player_id  UUID REFERENCES public.users(id),
    cells      TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE public.moves (
    id         BIGSERIAL PRIMARY KEY,
    game_id    UUID NOT NULL REFERENCES public.games(id),
    player_id  UUID REFERENCES public.users(id),
    x          SMALLINT NOT NULL,
    y          SMALLINT NOT NULL,
    hit        BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE public.game_history (
    id           BIGSERIAL PRIMARY KEY,
    game_id      UUID NOT NULL REFERENCES public.games(id),
    player_id    UUID NOT NULL REFERENCES public.users(id),
    opponent_id  UUID REFERENCES public.users(id),
    result       VARCHAR(10) NOT NULL,
    delta_rating INTEGER DEFAULT 0,
    created_at   TIMESTAMP WITH TIME ZONE
);

-- Indexes for common queries
CREATE INDEX idx_games_room_token ON public.games(room_token);
CREATE INDEX idx_games_host_id ON public.games(host_id);
CREATE INDEX idx_games_guest_id ON public.games(guest_id);
CREATE INDEX idx_boards_game_id ON public.boards(game_id);
CREATE INDEX idx_boards_game_player ON public.boards(game_id, player_id);
CREATE INDEX idx_moves_game_id ON public.moves(game_id);
CREATE INDEX idx_game_history_game_id ON public.game_history(game_id);
CREATE INDEX idx_game_history_player_id ON public.game_history(player_id);
CREATE INDEX idx_rooms_token ON public.rooms(token);
