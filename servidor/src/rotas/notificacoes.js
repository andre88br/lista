import { Router } from 'express';
import { texto } from '../validacao.js';

/** Guarda o token do FCM do aparelho, para o servidor poder avisar por push. */
export function rotasDeNotificacoes(pool) {
  const rotas = Router();

  rotas.put('/token', async (req, res) => {
    const token = texto(req.body?.token, { max: 4096, obrigatorio: true });
    await pool.query('UPDATE dispositivos SET fcm_token = $1 WHERE id = $2', [token, req.dispositivo.id]);
    res.json({ ok: true });
  });

  return rotas;
}
