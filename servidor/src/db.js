import { readFile, readdir } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import pg from 'pg';

const AQUI = dirname(fileURLToPath(import.meta.url));
const PASTA_SQL = join(AQUI, '..', 'sql');

export function criarPool(urlDoBanco = process.env.DATABASE_URL) {
  if (!urlDoBanco) throw new Error('DATABASE_URL nao definida');
  return new pg.Pool({ connectionString: urlDoBanco, max: 10 });
}

/**
 * Aplica os arquivos de sql/ em ordem, uma unica vez cada.
 * Roda no arranque do servidor: subir o contêiner ja deixa o banco pronto.
 */
export async function migrar(pool) {
  await pool.query(`
    CREATE TABLE IF NOT EXISTS migracoes (
      arquivo    text PRIMARY KEY,
      aplicada_em timestamptz NOT NULL DEFAULT now()
    )`);

  const arquivos = (await readdir(PASTA_SQL)).filter((n) => n.endsWith('.sql')).sort();
  for (const arquivo of arquivos) {
    const { rowCount } = await pool.query('SELECT 1 FROM migracoes WHERE arquivo = $1', [arquivo]);
    if (rowCount > 0) continue;

    const sql = await readFile(join(PASTA_SQL, arquivo), 'utf8');
    const cliente = await pool.connect();
    try {
      await cliente.query('BEGIN');
      await cliente.query(sql);
      await cliente.query('INSERT INTO migracoes (arquivo) VALUES ($1)', [arquivo]);
      await cliente.query('COMMIT');
      console.log(`[db] migracao aplicada: ${arquivo}`);
    } catch (erro) {
      await cliente.query('ROLLBACK');
      throw new Error(`falha na migracao ${arquivo}: ${erro.message}`);
    } finally {
      cliente.release();
    }
  }
}

/** Executa uma funcao dentro de uma transacao. */
export async function emTransacao(pool, fn) {
  const cliente = await pool.connect();
  try {
    await cliente.query('BEGIN');
    const resultado = await fn(cliente);
    await cliente.query('COMMIT');
    return resultado;
  } catch (erro) {
    await cliente.query('ROLLBACK');
    throw erro;
  } finally {
    cliente.release();
  }
}
