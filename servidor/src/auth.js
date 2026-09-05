import { hashDoToken } from './codigo.js';

/**
 * Autenticacao pelo token do aparelho, entregue no login com Google.
 * Preenche req.dispositivo com { id, usuarioId, casaId, nome }.
 */
export function autenticar(pool) {
  return async function (req, res, proximo) {
    const cabecalho = req.get('authorization') || '';
    const token = cabecalho.startsWith('Bearer ') ? cabecalho.slice(7).trim() : null;
    if (!token) return res.status(401).json({ erro: 'faca login no app' });

    const { rows } = await pool.query(
      `SELECT d.id, d.usuario_id, u.nome, m.casa_id
         FROM dispositivos d
         JOIN usuarios u ON u.id = d.usuario_id
         LEFT JOIN membros m ON m.usuario_id = d.usuario_id
        WHERE d.token_hash = $1`,
      [hashDoToken(token)],
    );
    if (rows.length === 0) return res.status(401).json({ erro: 'sessao invalida; entre de novo' });

    req.dispositivo = {
      id: rows[0].id,
      usuarioId: rows[0].usuario_id,
      nome: rows[0].nome,
      casaId: rows[0].casa_id,
    };

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

/** Exige que a pessoa ja esteja numa casa. */
export function exigirCasa(req, res, proximo) {
  if (!req.dispositivo?.casaId) {
    return res.status(409).json({ erro: 'voce ainda nao esta em nenhuma casa' });
  }
  proximo();
}
