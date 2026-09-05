import test from 'node:test';
import assert from 'node:assert/strict';
import { entrarComGoogle, entrarComGoogleCompleto, evento, subirServidor } from './ajuda.js';

const { base, chamar } = await subirServidor();

test('saude responde sem token', async () => {
  const { status, corpo } = await chamar('GET', '/saude');
  assert.equal(status, 200);
  assert.equal(corpo.ok, true);
});

test('rota protegida recusa quem nao fez login', async () => {
  const { status } = await chamar('GET', '/v1/casas/atual');
  assert.equal(status, 401);
});

test('sessao invalida e recusada', async () => {
  const { status } = await chamar('GET', '/v1/casas/atual', { token: 'token-inventado' });
  assert.equal(status, 401);
});

test('cria a casa e a outra pessoa entra pelo codigo', async () => {
  const tokenA = await entrarComGoogle(chamar, 'andre|André');
  const tokenB = await entrarComGoogle(chamar, 'maria|Maria');

  const criada = await chamar('POST', '/v1/casas', { token: tokenA, corpo: { nome: 'Casa' } });
  assert.equal(criada.status, 201);
  assert.match(criada.corpo.codigo, /^[0-9A-Z]{4}-[0-9A-Z]{4}$/);
  assert.equal(criada.corpo.membros, 1);

  // Codigo digitado de qualquer jeito tem que funcionar.
  const codigoBagunçado = criada.corpo.codigo.toLowerCase().replace('-', ' ');
  const entrou = await chamar('POST', '/v1/casas/entrar', {
    token: tokenB,
    corpo: { codigo: codigoBagunçado },
  });
  assert.equal(entrou.status, 200);
  assert.equal(entrou.corpo.casaId, criada.corpo.casaId);

  const atual = await chamar('GET', '/v1/casas/atual', { token: tokenB });
  assert.equal(atual.corpo.membros, 2);
});

test('codigo errado nao entra em casa nenhuma', async () => {
  const token = await entrarComGoogle(chamar, 'pessoa1|Pessoa 1');
  const { status } = await chamar('POST', '/v1/casas/entrar', { token, corpo: { codigo: 'ZZZZ-ZZZZ' } });
  assert.equal(status, 404);
});

test('sem casa, sincronizar da erro claro em vez de dado solto', async () => {
  const token = await entrarComGoogle(chamar, 'pessoa2|Pessoa 2');
  const { status, corpo } = await chamar('POST', '/v1/sync', { token, corpo: { eventos: [] } });
  assert.equal(status, 409);
  assert.match(corpo.erro, /nao esta em nenhuma casa/);
});

test('o que um celular envia chega no outro', async () => {
  const tokenA = await entrarComGoogle(chamar, 'pessoa3|Pessoa 3');
  const tokenB = await entrarComGoogle(chamar, 'pessoa4|Pessoa 4');
  const casa = await chamar('POST', '/v1/casas', { token: tokenA, corpo: { nome: 'Casa 2' } });
  await chamar('POST', '/v1/casas/entrar', { token: tokenB, corpo: { codigo: casa.corpo.codigo } });

  const envio = await chamar('POST', '/v1/sync', {
    token: tokenA,
    corpo: {
      eventos: [evento('7891000100103', { estoque: -1, lista: 1 })],
      produtos: [{ codigoBarras: '7891000100103', nome: 'Leite Ninho', atualizadoEm: new Date().toISOString() }],
    },
  });
  assert.equal(envio.status, 200);
  assert.equal(envio.corpo.eventosAceitos, 1);

  const leitura = await chamar('GET', '/v1/sync?desde=0', { token: tokenB });
  assert.equal(leitura.corpo.eventos.length, 1);
  assert.equal(leitura.corpo.eventos[0].deltaLista, 1);
  assert.equal(leitura.corpo.produtos[0].nome, 'Leite Ninho');
});

test('mandar o mesmo evento duas vezes nao conta duas vezes', async () => {
  const token = await entrarComGoogle(chamar, 'pessoa5|Pessoa 5');
  await chamar('POST', '/v1/casas', { token, corpo: { nome: 'Casa 3' } });

  const umEvento = evento('789', { lista: 1 });
  const primeira = await chamar('POST', '/v1/sync', { token, corpo: { eventos: [umEvento] } });
  const segunda = await chamar('POST', '/v1/sync', { token, corpo: { eventos: [umEvento] } });

  assert.equal(primeira.corpo.eventosAceitos, 1);
  assert.equal(segunda.corpo.eventosAceitos, 0, 'reenvio nao pode duplicar');

  const instantaneo = await chamar('GET', '/v1/sync/instantaneo', { token });
  assert.equal(instantaneo.corpo.itens.find((i) => i.codigoBarras === '789').lista, 1);
});

test('duas pessoas offline somam sem se atropelar', async () => {
  const tokenA = await entrarComGoogle(chamar, 'pessoa6|Pessoa 6');
  const tokenB = await entrarComGoogle(chamar, 'pessoa7|Pessoa 7');
  const casa = await chamar('POST', '/v1/casas', { token: tokenA, corpo: { nome: 'Casa 4' } });
  await chamar('POST', '/v1/casas/entrar', { token: tokenB, corpo: { codigo: casa.corpo.codigo } });

  // Os dois escaneiam o mesmo arroz "acabou" antes de qualquer um ter sinal.
  await chamar('POST', '/v1/sync', { token: tokenA, corpo: { eventos: [evento('arroz', { estoque: -1, lista: 1 })] } });
  await chamar('POST', '/v1/sync', { token: tokenB, corpo: { eventos: [evento('arroz', { estoque: -1, lista: 1 })] } });

  const instantaneo = await chamar('GET', '/v1/sync/instantaneo', { token: tokenA });
  const arroz = instantaneo.corpo.itens.find((i) => i.codigoBarras === 'arroz');
  assert.equal(arroz.lista, 2, 'as duas leituras contam');
  assert.equal(arroz.estoque, -2, 'a soma crua e guardada; quem exibe e que limita em zero');
});

test('um aparelho de outra casa nao enxerga nada', async () => {
  const tokenA = await entrarComGoogle(chamar, 'pessoa8|Pessoa 8');
  const tokenIntruso = await entrarComGoogle(chamar, 'pessoa9|Pessoa 9');
  await chamar('POST', '/v1/casas', { token: tokenA, corpo: { nome: 'Casa da familia' } });
  await chamar('POST', '/v1/casas', { token: tokenIntruso, corpo: { nome: 'Casa do estranho' } });

  await chamar('POST', '/v1/sync', {
    token: tokenA,
    corpo: {
      eventos: [evento('segredo', { lista: 1 })],
      produtos: [{ codigoBarras: 'segredo', nome: 'Cerveja', atualizadoEm: new Date().toISOString() }],
    },
  });

  const leitura = await chamar('GET', '/v1/sync?desde=0', { token: tokenIntruso });
  assert.equal(leitura.corpo.eventos.length, 0);
  assert.equal(leitura.corpo.produtos.length, 0);

  const instantaneo = await chamar('GET', '/v1/sync/instantaneo', { token: tokenIntruso });
  assert.equal(instantaneo.corpo.itens.length, 0);
});

test('o cursor traz so o que e novo', async () => {
  const token = await entrarComGoogle(chamar, 'pessoa10|Pessoa 10');
  await chamar('POST', '/v1/casas', { token, corpo: { nome: 'Casa 5' } });

  await chamar('POST', '/v1/sync', { token, corpo: { eventos: [evento('a', { lista: 1 })] } });
  const primeira = await chamar('GET', '/v1/sync?desde=0', { token });
  assert.equal(primeira.corpo.eventos.length, 1);

  await chamar('POST', '/v1/sync', { token, corpo: { eventos: [evento('b', { lista: 1 })] } });
  const segunda = await chamar('GET', `/v1/sync?desde=${primeira.corpo.seq}`, { token });
  const codigos = segunda.corpo.eventos.map((e) => e.codigoBarras);
  assert.ok(codigos.includes('b'), 'o evento novo tem que vir');
  assert.ok(segunda.corpo.seq > primeira.corpo.seq);
});

test('nome de produto: vence a escrita mais recente', async () => {
  const token = await entrarComGoogle(chamar, 'pessoa11|Pessoa 11');
  await chamar('POST', '/v1/casas', { token, corpo: { nome: 'Casa 6' } });

  const antes = new Date(Date.now() - 60_000).toISOString();
  const agora = new Date().toISOString();

  await chamar('POST', '/v1/sync', {
    token,
    corpo: { produtos: [{ codigoBarras: 'p1', nome: 'Nome novo', atualizadoEm: agora }] },
  });
  await chamar('POST', '/v1/sync', {
    token,
    corpo: { produtos: [{ codigoBarras: 'p1', nome: 'Nome velho', atualizadoEm: antes }] },
  });

  const instantaneo = await chamar('GET', '/v1/sync/instantaneo', { token });
  assert.equal(instantaneo.corpo.produtos[0].nome, 'Nome novo');
});

test('trocar o codigo invalida o antigo', async () => {
  const token = await entrarComGoogle(chamar, 'pessoa12|Pessoa 12');
  const outro = await entrarComGoogle(chamar, 'pessoa13|Pessoa 13');
  const casa = await chamar('POST', '/v1/casas', { token, corpo: { nome: 'Casa 7' } });
  const codigoAntigo = casa.corpo.codigo;

  const novo = await chamar('POST', '/v1/casas/codigo', { token });
  assert.notEqual(novo.corpo.codigo, codigoAntigo);

  const comAntigo = await chamar('POST', '/v1/casas/entrar', { token: outro, corpo: { codigo: codigoAntigo } });
  assert.equal(comAntigo.status, 404);

  const comNovo = await chamar('POST', '/v1/casas/entrar', { token: outro, corpo: { codigo: novo.corpo.codigo } });
  assert.equal(comNovo.status, 200);
});

test('sair da casa nao apaga o que ficou para o outro', async () => {
  const tokenA = await entrarComGoogle(chamar, 'pessoa14|Pessoa 14');
  const tokenB = await entrarComGoogle(chamar, 'pessoa15|Pessoa 15');
  const casa = await chamar('POST', '/v1/casas', { token: tokenA, corpo: { nome: 'Casa 8' } });
  await chamar('POST', '/v1/casas/entrar', { token: tokenB, corpo: { codigo: casa.corpo.codigo } });
  await chamar('POST', '/v1/sync', { token: tokenB, corpo: { eventos: [evento('cafe', { lista: 1 })] } });

  const saida = await chamar('DELETE', '/v1/casas/atual', { token: tokenB });
  assert.equal(saida.status, 200);

  const aindaLa = await chamar('GET', '/v1/sync/instantaneo', { token: tokenA });
  assert.equal(aindaLa.corpo.itens.find((i) => i.codigoBarras === 'cafe').lista, 1);

  const semCasa = await chamar('GET', '/v1/sync/instantaneo', { token: tokenB });
  assert.equal(semCasa.status, 409);
});

test('entrada invalida vira erro 400, nao erro 500', async () => {
  const token = await entrarComGoogle(chamar, 'pessoa16|Pessoa 16');
  await chamar('POST', '/v1/casas', { token, corpo: { nome: 'Casa 9' } });

  const semId = await chamar('POST', '/v1/sync', {
    token,
    corpo: { eventos: [{ codigoBarras: 'x', deltaLista: 1 }] },
  });
  assert.equal(semId.status, 400);

  const deltaAbsurdo = await chamar('POST', '/v1/sync', {
    token,
    corpo: { eventos: [{ ...evento('x'), deltaLista: 999999 }] },
  });
  assert.equal(deltaAbsurdo.status, 400);
});

test('a raiz responde uma pagina legivel, e nao erro de rota', async () => {
  const resposta = await fetch(`${base}/`);
  assert.equal(resposta.status, 200);
  assert.match(resposta.headers.get('content-type') || '', /text\/html/);
  assert.match(await resposta.text(), /Servidor no ar/);
});

test('a mesma pessoa em outro celular ja entra na casa, sem codigo', async () => {
  const primeiro = await entrarComGoogle(chamar, 'joao|João', 'celular velho');
  const casa = await chamar('POST', '/v1/casas', { token: primeiro, corpo: { nome: 'Casa do João' } });
  await chamar('POST', '/v1/sync', { token: primeiro, corpo: { eventos: [evento('cafe', { lista: 1 })] } });

  // Trocou de celular: mesmo login do Google, aparelho novo.
  const noCelularNovo = await entrarComGoogleCompleto(chamar, 'joao|João', 'celular novo');
  assert.equal(noCelularNovo.status, 200);
  assert.equal(noCelularNovo.corpo.casa.casaId, casa.corpo.casaId, 'a casa tem que vir junto no login');

  const instantaneo = await chamar('GET', '/v1/sync/instantaneo', { token: noCelularNovo.corpo.token });
  assert.equal(instantaneo.corpo.itens.find((i) => i.codigoBarras === 'cafe').lista, 1);
});

test('cada aparelho tem seu token: sair de um nao derruba o outro', async () => {
  const tokenVelho = await entrarComGoogle(chamar, 'ana|Ana', 'celular 1');
  const tokenNovo = await entrarComGoogle(chamar, 'ana|Ana', 'celular 2');
  assert.notEqual(tokenVelho, tokenNovo);

  await chamar('POST', '/v1/casas', { token: tokenNovo, corpo: { nome: 'Casa da Ana' } });
  const pelosDois = await Promise.all([
    chamar('GET', '/v1/casas/atual', { token: tokenVelho }),
    chamar('GET', '/v1/casas/atual', { token: tokenNovo }),
  ]);
  assert.equal(pelosDois[0].corpo.casaId, pelosDois[1].corpo.casaId, 'os dois aparelhos veem a mesma casa');
});

test('login invalido nao entra', async () => {
  const { status } = await chamar('POST', '/v1/auth/google', { corpo: { idToken: 'token-invalido' } });
  assert.equal(status, 401);
});

test('login sem token do Google da erro claro', async () => {
  const { status } = await chamar('POST', '/v1/auth/google', { corpo: {} });
  assert.equal(status, 400);
});

test('a leitura guarda quem escaneou', async () => {
  const tokenA = await entrarComGoogle(chamar, 'pedro|Pedro');
  const tokenB = await entrarComGoogle(chamar, 'clara|Clara');
  const casa = await chamar('POST', '/v1/casas', { token: tokenA, corpo: { nome: 'Casa 10' } });
  await chamar('POST', '/v1/casas/entrar', { token: tokenB, corpo: { codigo: casa.corpo.codigo } });

  await chamar('POST', '/v1/sync', { token: tokenB, corpo: { eventos: [evento('leite', { lista: 1 })] } });

  const leitura = await chamar('GET', '/v1/sync?desde=0', { token: tokenA });
  const doLeite = leitura.corpo.eventos.find((e) => e.codigoBarras === 'leite');
  assert.equal(doLeite.autorNome, 'Clara', 'quem escaneou tem que aparecer para o outro');

  const instantaneo = await chamar('GET', '/v1/sync/instantaneo', { token: tokenA });
  assert.equal(instantaneo.corpo.itens.find((i) => i.codigoBarras === 'leite').ultimoAutorNome, 'Clara');
});
