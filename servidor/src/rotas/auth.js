import { Router } from 'express';
import { emTransacao } from '../db.js';
import { gerarToken, hashDoToken } from '../codigo.js';
import { criarLimitador } from '../limite.js';
import { texto } from '../validacao.js';

/**
 * Entrada pelo Google. E a unica porta de entrada: o app exige login na
 * primeira abertura, e a partir dai guarda o token do aparelho e funciona
 * offline.
 */
export function rotasDeAuth(pool, verificarGoogle) {
  const rotas = Router();
  const limitePorIp = criarLimitador({ maximo: 30, janelaMs: 60 * 60 * 1000 });

  rotas.post('/google', async (req, res) => {
    if (!limitePorIp(req.ip)) {
      return res.status(429).json({ erro: 'muitas tentativas deste endereco; tente mais tarde' });
    }

    const idToken = texto(req.body?.idToken, { max: 4096, obrigatorio: true });
    const nomeDoAparelho = texto(req.body?.aparelho, { max: 80 });

    const pessoa = await verificarGoogle(idToken);

    const resultado = await emTransacao(pool, async (cliente) => {
      // O "sub" do Google e o que identifica a pessoa entre trocas de celular.
      const { rows: usuarios } = await cliente.query(
        `INSERT INTO usuarios (google_sub, email, nome, foto_url)
         VALUES ($1, $2, $3, $4)
         ON CONFLICT (google_sub) DO UPDATE
           SET email = EXCLUDED.email,
               nome = EXCLUDED.nome,
               foto_url = EXCLUDED.foto_url,
               ultimo_acesso = now()
         RETURNING id, nome, email, foto_url`,
        [pessoa.sub, pessoa.email, pessoa.nome, pessoa.fotoUrl],
      );
      const usuario = usuarios[0];

      // Cada instalacao ganha seu proprio token: sair num aparelho nao derruba o outro.
      const token = gerarToken();
      const { rows: dispositivos } = await cliente.query(
        'INSERT INTO dispositivos (token_hash, nome, usuario_id) VALUES ($1, $2, $3) RETURNING id',
        [hashDoToken(token), nomeDoAparelho, usuario.id],
      );

      // A casa segue a pessoa: no celular novo, ela ja entra na casa certa.
      const { rows: casas } = await cliente.query(
        `SELECT c.id, c.nome, c.codigo,
                (SELECT count(*) FROM membros m2 WHERE m2.casa_id = c.id) AS membros
           FROM membros m
           JOIN casas c ON c.id = m.casa_id
          WHERE m.usuario_id = $1`,
        [usuario.id],
      );

      return { usuario, token, dispositivoId: dispositivos[0].id, casa: casas[0] ?? null };
    });

    res.json({
      token: resultado.token,
      dispositivoId: resultado.dispositivoId,
      usuario: {
        id: resultado.usuario.id,
        nome: resultado.usuario.nome,
        email: resultado.usuario.email,
        fotoUrl: resultado.usuario.foto_url,
      },
      casa: resultado.casa
        ? {
            casaId: resultado.casa.id,
            nome: resultado.casa.nome,
            codigo: resultado.casa.codigo,
            membros: Number(resultado.casa.membros),
          }
        : null,
    });
  });

  return rotas;
}
