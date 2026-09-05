import { Router } from 'express';
import { emTransacao } from '../db.js';
import { gerarCodigoConvite, normalizarCodigo } from '../codigo.js';
import { criarLimitador } from '../limite.js';
import { exigirCasa } from '../auth.js';
import { texto } from '../validacao.js';

async function dadosDaCasa(pool, casaId) {
  const { rows } = await pool.query(
    `SELECT c.id, c.nome, c.codigo, c.criada_em,
            (SELECT count(*) FROM membros m WHERE m.casa_id = c.id) AS membros
       FROM casas c WHERE c.id = $1`,
    [casaId],
  );
  if (rows.length === 0) return null;
  const casa = rows[0];
  return {
    casaId: casa.id,
    nome: casa.nome,
    codigo: casa.codigo,
    criadaEm: casa.criada_em,
    membros: Number(casa.membros),
  };
}

export function rotasDeCasas(pool) {
  const rotas = Router();
  // Codigo de convite tem ~40 bits; ainda assim, limitar tentativas fecha a porta para forca bruta.
  const limiteEntrar = criarLimitador({ maximo: 10, janelaMs: 60 * 60 * 1000 });

  rotas.post('/', async (req, res) => {
    if (req.dispositivo.casaId) {
      return res.status(409).json({
        erro: 'voce ja esta em uma casa; saia dela antes de criar outra',
        casa: await dadosDaCasa(pool, req.dispositivo.casaId),
      });
    }

    const nome = texto(req.body?.nome, { max: 80 }) || 'Minha casa';
    const casa = await emTransacao(pool, async (cliente) => {
      // Colisao de codigo e improvavel, mas tentar de novo custa pouco.
      for (let tentativa = 0; tentativa < 5; tentativa++) {
        const codigo = gerarCodigoConvite();
        try {
          const { rows } = await cliente.query(
            'INSERT INTO casas (nome, codigo) VALUES ($1, $2) RETURNING id',
            [nome, codigo],
          );
          await cliente.query(
            'INSERT INTO membros (casa_id, usuario_id) VALUES ($1, $2)',
            [rows[0].id, req.dispositivo.usuarioId],
          );
          return rows[0].id;
        } catch (erro) {
          if (erro.code !== '23505') throw erro;
        }
      }
      throw new Error('nao consegui gerar um codigo de convite unico');
    });

    res.status(201).json(await dadosDaCasa(pool, casa));
  });

  rotas.post('/entrar', async (req, res) => {
    if (!limiteEntrar(req.dispositivo.id)) {
      return res.status(429).json({ erro: 'muitas tentativas de codigo; espere um pouco' });
    }

    const codigo = normalizarCodigo(req.body?.codigo);
    if (!codigo) return res.status(400).json({ erro: 'codigo invalido' });

    const { rows } = await pool.query('SELECT id FROM casas WHERE codigo = $1', [codigo]);
    if (rows.length === 0) return res.status(404).json({ erro: 'nao encontrei nenhuma casa com esse codigo' });

    const casaId = rows[0].id;
    if (req.dispositivo.casaId && req.dispositivo.casaId !== casaId) {
      return res.status(409).json({ erro: 'voce ja esta em outra casa; saia dela primeiro' });
    }

    await pool.query(
      `INSERT INTO membros (casa_id, usuario_id) VALUES ($1, $2)
       ON CONFLICT (casa_id, usuario_id) DO NOTHING`,
      [casaId, req.dispositivo.usuarioId],
    );

    res.json(await dadosDaCasa(pool, casaId));
  });

  rotas.get('/atual', exigirCasa, async (req, res) => {
    res.json(await dadosDaCasa(pool, req.dispositivo.casaId));
  });

  // Trocar o codigo: util se o antigo foi compartilhado com quem nao devia.
  rotas.post('/codigo', exigirCasa, async (req, res) => {
    for (let tentativa = 0; tentativa < 5; tentativa++) {
      try {
        await pool.query('UPDATE casas SET codigo = $1 WHERE id = $2', [
          gerarCodigoConvite(),
          req.dispositivo.casaId,
        ]);
        return res.json(await dadosDaCasa(pool, req.dispositivo.casaId));
      } catch (erro) {
        if (erro.code !== '23505') throw erro;
      }
    }
    res.status(500).json({ erro: 'nao consegui gerar um codigo novo' });
  });

  // Sair da casa nao apaga os dados dela: os outros membros continuam com tudo.
  rotas.delete('/atual', exigirCasa, async (req, res) => {
    await pool.query('DELETE FROM membros WHERE usuario_id = $1', [req.dispositivo.usuarioId]);
    res.json({ ok: true });
  });

  return rotas;
}
