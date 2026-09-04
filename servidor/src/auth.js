import { hashDoToken } from './codigo.js';

/**
 * Autenticacao por token do aparelho (Authorization: Bearer <token>).
 * Preenche req.dispositivo com { id, casaId }.
 */
export function autenticar(pool) {
  return async function (req, res, proximo) {
    const cabecalho = req.get('authorization') || '';
    const token = cabecalho.startsWith('Bearer ') ? cabecalho.slice(7).trim() : null;
    if (!token) return res.status(401).json({ erro: 'informe o token do aparelho' });

    const { rows } = await pool.query(
      `SELECT d.id, m.casa_id
         FROM dispositivos d
         LEFT JOIN membros m ON m.dispositivo_id = d.id
        WHERE d.token_hash = $1`,
      [hashDoToken(token)],
    );
    if (rows.length === 0) return res.status(401).json({ erro: 'token invalido' });

    req.dispositivo = { id: rows[0].id, casaId: rows[0].casa_id };
    // Atualiza o ultimo acesso no maximo uma vez por hora, para nao escrever a cada chamada.
    pool
      .query(
        `UPDATE dispositivos SET ultimo_acesso = now()
          WHERE id = $1 AND ultimo_acesso < now() - interval '1 hour'`,
        [rows[0].id],
      )
      .catch(() => {});
    proximo();
  };
}

/** Exige que o aparelho ja esteja numa casa. */
export function exigirCasa(req, res, proximo) {
  if (!req.dispositivo?.casaId) {
    return res.status(409).json({ erro: 'este aparelho ainda nao esta em nenhuma casa' });
  }
  proximo();
}
