UPDATE public.users
SET avatar = '/api/avatars/' || SUBSTRING(avatar FROM LENGTH('/avatars/') + 1)
WHERE avatar LIKE '/avatars/%';

UPDATE public.users SET avatar = NULL WHERE avatar = '/default_avatar.png';

ALTER TABLE public.users ALTER COLUMN avatar DROP DEFAULT;
