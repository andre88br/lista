-- Estrutura inicial do servidor de sincronizacao.
-- Nao guarda dado pessoal: so o token do aparelho (em hash) e nomes de produto.

CREATE TABLE IF NOT EXISTS dispositivos (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    token_hash    text NOT NULL UNIQUE,
    nome          text,
    -- Gancho para, no futuro, vincular um e-mail e recuperar o acesso.
    email         text UNIQUE,
    senha_hash    text,
    criado_em     timestamptz NOT NULL DEFAULT now(),
    ultimo_acesso timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS casas (
    id        uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    nome      text NOT NULL,
    codigo    text NOT NULL UNIQUE,
    criada_em timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS membros (
    casa_id        uuid NOT NULL REFERENCES casas(id) ON DELETE CASCADE,
    dispositivo_id uuid NOT NULL REFERENCES dispositivos(id) ON DELETE CASCADE,
    entrou_em      timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (casa_id, dispositivo_id)
);

-- Um aparelho participa de uma casa por vez: simplifica a sincronizacao e a interface.
CREATE UNIQUE INDEX IF NOT EXISTS membros_um_por_dispositivo ON membros (dispositivo_id);

CREATE TABLE IF NOT EXISTS produtos (
    casa_id       uuid NOT NULL REFERENCES casas(id) ON DELETE CASCADE,
    codigo_barras text NOT NULL,
    nome          text NOT NULL,
    marca         text,
    imagem_url    text,
    categoria     text,
    atualizado_em timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (casa_id, codigo_barras)
);

CREATE INDEX IF NOT EXISTS produtos_por_atualizacao ON produtos (casa_id, atualizado_em);

-- O coracao da sincronizacao: cada leitura e um evento com deltas.
-- A quantidade de um produto e a soma dos deltas, e soma nao depende de ordem,
-- entao dois celulares offline chegam ao mesmo numero.
CREATE TABLE IF NOT EXISTS eventos (
    id             uuid PRIMARY KEY,
    casa_id        uuid NOT NULL REFERENCES casas(id) ON DELETE CASCADE,
    codigo_barras  text NOT NULL,
    modo           text NOT NULL,
    delta_estoque  integer NOT NULL DEFAULT 0,
    delta_lista    integer NOT NULL DEFAULT 0,
    delta_carrinho integer NOT NULL DEFAULT 0,
    autor_id       uuid REFERENCES dispositivos(id) ON DELETE SET NULL,
    criado_em      timestamptz NOT NULL DEFAULT now(),
    recebido_em    timestamptz NOT NULL DEFAULT now(),
    seq            bigserial NOT NULL
);

CREATE INDEX IF NOT EXISTS eventos_por_seq ON eventos (casa_id, seq);
CREATE INDEX IF NOT EXISTS eventos_por_recebimento ON eventos (casa_id, recebido_em);
