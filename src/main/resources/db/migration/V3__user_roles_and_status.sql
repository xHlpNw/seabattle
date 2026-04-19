ALTER TABLE public.users
    ADD COLUMN IF NOT EXISTS role VARCHAR(20),
    ADD COLUMN IF NOT EXISTS status VARCHAR(20);

UPDATE public.users
SET role = CASE
    WHEN username = 'admin' THEN 'ADMIN'
    ELSE COALESCE(role, 'USER')
END
WHERE role IS NULL OR role = '' OR username = 'admin';

UPDATE public.users
SET status = COALESCE(status, 'ACTIVE')
WHERE status IS NULL OR status = '';

ALTER TABLE public.users
    ALTER COLUMN role SET DEFAULT 'USER',
    ALTER COLUMN role SET NOT NULL,
    ALTER COLUMN status SET DEFAULT 'ACTIVE',
    ALTER COLUMN status SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_users_role ON public.users(role);
CREATE INDEX IF NOT EXISTS idx_users_status ON public.users(status);
