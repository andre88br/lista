-- Login com Google: a identidade passa a ser a pessoa, nao o aparelho.
-- E isso que permite trocar de celular sem perder a casa e mostrar quem fez
-- cada leitura.

CREATE TABLE IF NOT EXISTS usuarios (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    -- Identificador estavel do Google. O e-mail pode mudar; o "sub", nao.
    google_sub text UNIQUE,
    email      text,
    nome       text NOT NULL,
    foto_url   text,
    criado_em  timestamptz NOT NULL DEFAULT now(),
    ultimo_acesso timestamptz NOT NULL DEFAULT now()
);

-- Cada aparelho passa a pertencer a uma pessoa.
ALTER TABLE dispositivos ADD COLUMN IF NOT EXISTS usuario_id uuid REFERENCES usuarios(id) ON DELETE CASCADE;
CREATE INDEX IF NOT EXISTS dispositivos_por_usuario ON dispositivos (usuario_id);

-- Antes de trocar a chave de membros, cada aparelho ja cadastrado vira uma
-- pessoa sem Google, para nenhuma casa existente ficar orfa.
INSERT INTO usuarios (id, nome, criado_em)
SELECT gen_random_uuid(), COALESCE(d.nome, 'Aparelho'), d.criado_em
  FROM dispositivos d
 WHERE d.usuario_id IS NULL;

UPDATE dispositivos d
   SET usuario_id = u.id
  FROM usuarios u
 WHERE d.usuario_id IS NULL
   AND u.google_sub IS NULL
   AND u.nome = COALESCE(d.nome, 'Aparelho')
   AND u.criado_em = d.criado_em;

-- Participar da casa e da pessoa, nao do aparelho: e assim que o celular novo
-- ja entra na casa certa so com o login.
ALTER TABLE membros ADD COLUMN IF NOT EXISTS usuario_id uuid REFERENCES usuarios(id) ON DELETE CASCADE;

UPDATE membros m
   SET usuario_id = d.usuario_id
  FROM dispositivos d
 WHERE m.usuario_id IS NULL
   AND d.id = m.dispositivo_id;

DELETE FROM membros WHERE usuario_id IS NULL;

DROP INDEX IF EXISTS membros_um_por_dispositivo;
ALTER TABLE membros DROP CONSTRAINT IF EXISTS membros_pkey;
ALTER TABLE membros DROP COLUMN IF EXISTS dispositivo_id;
ALTER TABLE membros ALTER COLUMN usuario_id SET NOT NULL;
ALTER TABLE membros ADD PRIMARY KEY (casa_id, usuario_id);

-- Uma casa por pessoa: simplifica a interface e a sincronizacao.
CREATE UNIQUE INDEX IF NOT EXISTS membros_uma_casa_por_usuario ON membros (usuario_id);

-- Autoria da leitura: quem escaneou, e nao so qual aparelho.
ALTER TABLE eventos ADD COLUMN IF NOT EXISTS autor_usuario_id uuid REFERENCES usuarios(id) ON DELETE SET NULL;

UPDATE eventos e
   SET autor_usuario_id = d.usuario_id
  FROM dispositivos d
 WHERE e.autor_usuario_id IS NULL
   AND d.id = e.autor_id;
