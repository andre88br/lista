import { after } from 'node:test';
import { criarApp } from '../src/app.js';
import { criarPool, migrar } from '../src/db.js';

const URL_PADRAO = 'postgres://postgres@127.0.0.1:55432/postgres';

/**
 * Sobe o servidor de verdade contra um Postgres de verdade, num schema proprio,
 * e devolve um cliente HTTP simples. Nada de banco falso: o que passa aqui e o
 * mesmo caminho que roda no VPS.
 */
export async function subirServidor() {
  const urlBase = process.env.DATABASE_URL || URL_PADRAO;
  const schema = `teste_${Math.random().toString(36).slice(2, 10)}`;
  const url = `${urlBase}${urlBase.includes('?') ? '&' : '?'}options=-c%20search_path%3D${schema}`;

  const poolAdmin = criarPool(urlBase);
  await poolAdmin.query(`CREATE SCHEMA ${schema}`);
  await poolAdmin.end();

  const pool = criarPool(url);
  await migrar(pool);

  const app = criarApp(pool);
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

/** Registra um aparelho e devolve o token dele. */
export async function novoAparelho(chamar, nome = 'celular de teste') {
  const { status, corpo } = await chamar('POST', '/v1/dispositivos', { corpo: { nome } });
  if (status !== 201) throw new Error(`falha ao registrar aparelho: ${status}`);
  return corpo.token;
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
