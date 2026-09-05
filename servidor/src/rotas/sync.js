import { Router } from 'express';
import { emTransacao } from '../db.js';
import { exigirCasa } from '../auth.js';
import { dataDoCliente, inteiro, lista, texto, uuid } from '../validacao.js';

const MODOS = new Set(['MERCADO', 'GUARDAR', 'ACABOU', 'AJUSTE', 'DESFAZER']);
const LIMITE_PAGINA = 500;
// Reenviar os ultimos minutos em toda leitura fecha a brecha de uma transacao
// que confirma fora de ordem. Como aplicar o mesmo evento duas vezes nao faz
// efeito, repetir e barato e perder um evento nao seria.
const JANELA_SOBREPOSICAO = "5 minutes";

function eventoParaJson(linha) {
  return {
    id: linha.id,
    codigoBarras: linha.codigo_barras,
    modo: linha.modo,
    deltaEstoque: linha.delta_estoque,
    deltaLista: linha.delta_lista,
    deltaCarrinho: linha.delta_carrinho,
    autorId: linha.autor_usuario_id,
    autorNome: linha.autor_nome ?? null,
    criadoEm: linha.criado_em,
    seq: Number(linha.seq),
  };
}

function produtoParaJson(linha) {
  return {
    codigoBarras: linha.codigo_barras,
    nome: linha.nome,
    marca: linha.marca,
    imagemUrl: linha.imagem_url,
    categoria: linha.categoria,
    atualizadoEm: linha.atualizado_em,
  };
}

export function rotasDeSync(pool, avisador) {
  const rotas = Router();
  rotas.use(exigirCasa);

  // Envio: eventos e produtos que o celular ainda nao mandou.
  rotas.post('/', async (req, res) => {
    const eventos = lista(req.body?.eventos, { max: LIMITE_PAGINA }).map((e) => ({
      id: uuid(e?.id),
      codigoBarras: texto(e?.codigoBarras, { max: 120, obrigatorio: true }),
      modo: MODOS.has(e?.modo) ? e.modo : 'AJUSTE',
      deltaEstoque: inteiro(e?.deltaEstoque, { min: -9999, max: 9999 }),
      deltaLista: inteiro(e?.deltaLista, { min: -9999, max: 9999 }),
      deltaCarrinho: inteiro(e?.deltaCarrinho, { min: -9999, max: 9999 }),
      criadoEm: dataDoCliente(e?.criadoEm),
    }));

    const produtos = lista(req.body?.produtos, { max: LIMITE_PAGINA }).map((p) => ({
      codigoBarras: texto(p?.codigoBarras, { max: 120, obrigatorio: true }),
      nome: texto(p?.nome, { max: 200, obrigatorio: true }),
      marca: texto(p?.marca, { max: 120 }),
      imagemUrl: texto(p?.imagemUrl, { max: 500 }),
      categoria: texto(p?.categoria, { max: 120 }),
      atualizadoEm: dataDoCliente(p?.atualizadoEm),
    }));

    const casaId = req.dispositivo.casaId;
    const resultado = await emTransacao(pool, async (cliente) => {
      let eventosAceitos = 0;
      for (const evento of eventos) {
        // O id vem do celular, entao reenviar o mesmo evento nao conta duas vezes.
        const { rowCount } = await cliente.query(
          `INSERT INTO eventos
             (id, casa_id, codigo_barras, modo, delta_estoque, delta_lista, delta_carrinho,
              autor_id, autor_usuario_id, criado_em)
           VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
           ON CONFLICT (id) DO NOTHING`,
          [
            evento.id, casaId, evento.codigoBarras, evento.modo,
            evento.deltaEstoque, evento.deltaLista, evento.deltaCarrinho,
            req.dispositivo.id, req.dispositivo.usuarioId, evento.criadoEm,
          ],
        );
        eventosAceitos += rowCount;
      }

      for (const produto of produtos) {
        // Nome de produto nao e contador: vence quem escreveu por ultimo.
        await cliente.query(
          `INSERT INTO produtos (casa_id, codigo_barras, nome, marca, imagem_url, categoria, atualizado_em)
           VALUES ($1, $2, $3, $4, $5, $6, $7)
           ON CONFLICT (casa_id, codigo_barras) DO UPDATE
             SET nome = EXCLUDED.nome, marca = EXCLUDED.marca,
                 imagem_url = EXCLUDED.imagem_url, categoria = EXCLUDED.categoria,
                 atualizado_em = EXCLUDED.atualizado_em
           WHERE EXCLUDED.atualizado_em > produtos.atualizado_em`,
          [
            casaId, produto.codigoBarras, produto.nome, produto.marca,
            produto.imagemUrl, produto.categoria, produto.atualizadoEm,
          ],
        );
      }

      const { rows } = await cliente.query(
        'SELECT COALESCE(MAX(seq), 0) AS seq FROM eventos WHERE casa_id = $1',
        [casaId],
      );
      return { eventosAceitos, produtosRecebidos: produtos.length, seq: Number(rows[0].seq) };
    });

    if (resultado.eventosAceitos > 0 || resultado.produtosRecebidos > 0) {
      avisador.avisar(casaId, { seq: resultado.seq });
    }
    res.json(resultado);
  });

  // Leitura incremental a partir do cursor.
  rotas.get('/', async (req, res) => {
    const casaId = req.dispositivo.casaId;
    const desde = Number.parseInt(req.query.desde ?? '0', 10) || 0;
    const produtosDesde = req.query.produtosDesde ? new Date(String(req.query.produtosDesde)) : null;
    const produtosDesdeValido =
      produtosDesde && !Number.isNaN(produtosDesde.getTime()) ? produtosDesde : new Date(0);

    const eventos = await pool.query(
      `SELECT e.*, u.nome AS autor_nome
         FROM eventos e
         LEFT JOIN usuarios u ON u.id = e.autor_usuario_id
        WHERE e.casa_id = $1
          AND (e.seq > $2 OR e.recebido_em > now() - interval '${JANELA_SOBREPOSICAO}')
        ORDER BY e.seq
        LIMIT ${LIMITE_PAGINA}`,
      [casaId, desde],
    );

    const produtos = await pool.query(
      `SELECT * FROM produtos
        WHERE casa_id = $1
          AND atualizado_em > $2::timestamptz - interval '${JANELA_SOBREPOSICAO}'
        ORDER BY atualizado_em
        LIMIT ${LIMITE_PAGINA}`,
      [casaId, produtosDesdeValido.toISOString()],
    );

    const maiorSeq = eventos.rows.reduce((maior, l) => Math.max(maior, Number(l.seq)), desde);
    res.json({
      seq: maiorSeq,
      agora: new Date().toISOString(),
      temMais: eventos.rowCount === LIMITE_PAGINA || produtos.rowCount === LIMITE_PAGINA,
      eventos: eventos.rows.map(eventoParaJson),
      produtos: produtos.rows.map(produtoParaJson),
    });
  });

  // Estado completo da casa: usado por quem acabou de entrar pelo codigo.
  rotas.get('/instantaneo', async (req, res) => {
    const casaId = req.dispositivo.casaId;
    const itens = await pool.query(
      `SELECT codigo_barras,
              SUM(delta_estoque)::int  AS estoque,
              SUM(delta_lista)::int    AS lista,
              SUM(delta_carrinho)::int AS carrinho
         FROM eventos WHERE casa_id = $1
         GROUP BY codigo_barras`,
      [casaId],
    );
    const produtos = await pool.query('SELECT * FROM produtos WHERE casa_id = $1', [casaId]);
    const autores = await pool.query(
      `SELECT DISTINCT ON (e.codigo_barras) e.codigo_barras, u.nome AS autor_nome
         FROM eventos e
         LEFT JOIN usuarios u ON u.id = e.autor_usuario_id
        WHERE e.casa_id = $1
        ORDER BY e.codigo_barras, e.seq DESC`,
      [casaId],
    );
    const nomePorCodigo = new Map(autores.rows.map((l) => [l.codigo_barras, l.autor_nome]));
    const seq = await pool.query(
      'SELECT COALESCE(MAX(seq), 0) AS seq FROM eventos WHERE casa_id = $1',
      [casaId],
    );

    res.json({
      seq: Number(seq.rows[0].seq),
      agora: new Date().toISOString(),
      produtos: produtos.rows.map(produtoParaJson),
      // Somas cruas: quem exibe e que limita em zero.
      itens: itens.rows.map((l) => ({
        codigoBarras: l.codigo_barras,
        estoque: l.estoque,
        lista: l.lista,
        carrinho: l.carrinho,
        ultimoAutorNome: nomePorCodigo.get(l.codigo_barras) ?? null,
      })),
    });
  });

  return rotas;
}
