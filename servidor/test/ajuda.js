import { after } from 'node:test';
import { criarApp } from '../src/app.js';
import { criarPool, migrar } from '../src/db.js';

const URL_PADRAO = 'postgres://postgres@127.0.0.1:55432/postgres';

/**
 * Sobe o servidor de verdade contra um Postgres de verdade, num schema proprio,
 * e devolve um cliente HTTP simples. Nada de banco falso: o que passa aqui e o
 * mesmo caminho que roda no VPS.
 */
/**
 * Verificador de token falso: nos testes, o "token" e apenas o identificador da
 * pessoa. A verificacao de verdade contra o Google e testada por ela mesma
 * (assinatura e audiencia), e nao daria para reproduzir aqui sem uma conta real.
 */
export function verificadorFalso() {
  return async function (idToken) {
    if (idToken === 'token-invalido') {
      throw Object.assign(new Error('login do Google invalido ou expirado'), { status: 401 });
    }
    const [sub, nome, email] = idToken.split('|');
    return {
      sub,
      nome: nome || sub,
      email: email || `${sub}@exemplo.com`,
      fotoUrl: null,
    };
  };
}

export async function subirServidor() {
  const urlBase = process.env.DATABASE_URL || URL_PADRAO;
  const schema = `teste_${Math.random().toString(36).slice(2, 10)}`;
  const url = `${urlBase}${urlBase.includes('?') ? '&' : '?'}options=-c%20search_path%3D${schema}`;

  const poolAdmin = criarPool(urlBase);
  await poolAdmin.query(`CREATE SCHEMA ${schema}`);
  await poolAdmin.end();

  const pool = criarPool(url);
  await migrar(pool);

  const app = criarApp(pool, { verificarGoogle: verificadorFalso() });
  const servidor = app.listen(0);
  await new Promise((resolve) => servidor.once('listening', resolve));
  const base = `http://127.0.0.1:${servidor.address().port}`;

  async function encerrar() {
    await new Promise((resolve) => servidor.close(resolve));
    await pool.end();
    const limpeza = criarPool(urlBase);
    await limpeza.query(`DROP SCHEMA ${schema} CASCADE`);
    await limpeza.end();
  }
  after(encerrar);

  return { base, pool, app, encerrar, chamar: criarChamador(base) };
}

function criarChamador(base) {
  return async function chamar(metodo, caminho, { token, corpo } = {}) {
    const resposta = await fetch(`${base}${caminho}`, {
      method: metodo,
      headers: {
        ...(corpo ? { 'Content-Type': 'application/json' } : {}),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: corpo ? JSON.stringify(corpo) : undefined,
    });
    const texto = await resposta.text();
    let json = null;
    try {
      json = texto ? JSON.parse(texto) : null;
    } catch {
      json = { textoBruto: texto };
    }
    return { status: resposta.status, corpo: json };
  };
}

/**
 * Faz login e devolve o token do aparelho. [pessoa] identifica quem esta
 * entrando: a mesma pessoa em dois aparelhos cai na mesma casa.
 */
export async function entrarComGoogle(chamar, pessoa = 'andre', aparelho = 'celular de teste') {
  const { status, corpo } = await chamar('POST', '/v1/auth/google', {
    corpo: { idToken: pessoa, aparelho },
  });
  if (status !== 200) throw new Error(`falha no login: ${status} ${JSON.stringify(corpo)}`);
  return corpo.token;
}

/** Login devolvendo a resposta inteira, para os testes que olham a casa. */
export async function entrarComGoogleCompleto(chamar, pessoa, aparelho = 'celular') {
  return chamar('POST', '/v1/auth/google', { corpo: { idToken: pessoa, aparelho } });
}

export function evento(codigoBarras, deltas = {}) {
  return {
    id: crypto.randomUUID(),
    codigoBarras,
    modo: deltas.modo || 'ACABOU',
    deltaEstoque: deltas.estoque ?? 0,
    deltaLista: deltas.lista ?? 0,
    deltaCarrinho: deltas.carrinho ?? 0,
    criadoEm: new Date().toISOString(),
  };
}
