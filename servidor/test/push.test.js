import test from 'node:test';
import assert from 'node:assert/strict';
import { criarNotificador } from '../src/push.js';

test('sem credencial, o notificador nao faz nada e nao quebra', async () => {
  const notificador = criarNotificador(undefined);
  // Nao deve lancar, mesmo sem pool/casaId de verdade: e um no-op puro.
  await assert.doesNotReject(notificador.enviarParaCasa(null, 'qualquer-casa', 'qualquer-dispositivo'));
});

test('credencial invalida da erro claro, em vez de falhar mais tarde', () => {
  assert.throws(() => criarNotificador('isto nao e json'), /JSON valido/);
});
