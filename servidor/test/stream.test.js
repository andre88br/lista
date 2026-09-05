import test from 'node:test';
import assert from 'node:assert/strict';
import { entrarComGoogle, evento, subirServidor } from './ajuda.js';

const { base, chamar } = await subirServidor();

test('quem esta ouvindo recebe o aviso quando o outro envia algo', async () => {
  const tokenA = await entrarComGoogle(chamar, 'sse-a|A');
  const tokenB = await entrarComGoogle(chamar, 'sse-b|B');
  const casa = await chamar('POST', '/v1/casas', { token: tokenA, corpo: { nome: 'Casa SSE' } });
  await chamar('POST', '/v1/casas/entrar', { token: tokenB, corpo: { codigo: casa.corpo.codigo } });

  const cancelador = new AbortController();
  const resposta = await fetch(`${base}/v1/stream`, {
    headers: { Authorization: `Bearer ${tokenB}` },
    signal: cancelador.signal,
  });
  assert.equal(resposta.status, 200);

  const leitor = resposta.body.getReader();
  const decodificador = new TextDecoder();

  // O primeiro quadro confirma a conexao aberta.
  const primeiro = decodificador.decode((await leitor.read()).value);
  assert.match(primeiro, /event: conectado/);

  await chamar('POST', '/v1/sync', { token: tokenA, corpo: { eventos: [evento('cafe', { lista: 1 })] } });

  const aviso = decodificador.decode((await leitor.read()).value);
  assert.match(aviso, /event: novidade/);

  cancelador.abort();
  await leitor.cancel().catch(() => {});
});

test('sem casa, nao da para ouvir o stream de ninguem', async () => {
  const token = await entrarComGoogle(chamar, 'sse-c|C');
  const resposta = await fetch(`${base}/v1/stream`, { headers: { Authorization: `Bearer ${token}` } });
  assert.equal(resposta.status, 409);
  await resposta.body?.cancel().catch(() => {});
});
