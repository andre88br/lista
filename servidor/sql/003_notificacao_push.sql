-- Notificacao push: avisa o outro aparelho mesmo com o app fechado, sem
-- precisar esperar o ciclo periodico ou o app estar aberto para o SSE.

ALTER TABLE dispositivos ADD COLUMN IF NOT EXISTS fcm_token text;
