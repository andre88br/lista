import { Router } from 'express';
import { gerarToken, hashDoToken } from '../codigo.js';
import { criarLimitador } from '../limite.js';
import { texto } from '../validacao.js';

export function rotasDeDispositivos(pool) {
  const rotas = Router();
  const limitePorIp = criarLimitador({ maximo: 20, janelaMs: 60 * 60 * 1000 });

  // Registro do aparelho: e a unica rota sem token, porque e ela que entrega o token.
  rotas.post('/', async (req, res) => {
    if (!limitePorIp(req.ip)) {
      return res.status(429).json({ erro: 'muitos registros deste endereco; tente mais tarde' });
    }

    const nome = texto(req.body?.nome, { max: 80 });
    const token = gerarToken();
    const { rows } = await pool.query(
      'INSERT INTO dispositivos (token_hash, nome) VALUES ($1, $2) RETURNING id, criado_em',
      [hashDoToken(token), nome],
    );

    res.status(201).json({ dispositivoId: rows[0].id, token, criadoEm: rows[0].criado_em });
  });

  return rotas;
}
