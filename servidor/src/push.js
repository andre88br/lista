import admin from 'firebase-admin';

const CODIGOS_TOKEN_MORTO = new Set([
  'messaging/registration-token-not-registered',
  'messaging/invalid-registration-token',
]);

const SEM_PUSH = { enviarParaCasa: async () => {} };

/**
 * Avisa por push os outros aparelhos da casa quando algo muda, para sincronizar
 * mesmo com o app fechado (o SSE so alcanca quem esta com o app aberto).
 *
 * Sem credencial configurada, devolve um notificador que nao faz nada: a
 * notificacao push e inteiramente opcional, e o app continua funcionando pelo
 * SSE (app aberto) e pelo ciclo periodico em segundo plano.
 */
export function criarNotificador(credencialJson) {
  if (!credencialJson) {
    return SEM_PUSH;
  }

  // Uma credencial quebrada (JSON invalido, colado pela metade, chave errada)
  // nunca pode derrubar o servidor inteiro so por causa de um recurso
  // opcional: melhor desligar a notificacao e seguir com o resto no ar.
  let mensageria;
  try {
    const credencial = JSON.parse(credencialJson);
    const app = admin.initializeApp({ credential: admin.credential.cert(credencial) }, 'lista-push');
    mensageria = app.messaging();
  } catch (erro) {
    console.error('[push] FIREBASE_SERVICE_ACCOUNT_JSON invalido; notificacao push desligada:', erro.message);
    return SEM_PUSH;
  }

  /** Manda um "tem novidade" para todo aparelho da casa, menos quem enviou. */
  async function enviarParaCasa(pool, casaId, dispositivoRemetenteId) {
    const { rows } = await pool.query(
      `SELECT d.id, d.fcm_token
         FROM dispositivos d
         JOIN membros m ON m.usuario_id = d.usuario_id
        WHERE m.casa_id = $1 AND d.id != $2 AND d.fcm_token IS NOT NULL`,
      [casaId, dispositivoRemetenteId],
    );
    if (rows.length === 0) return;

    const resposta = await mensageria
      .sendEachForMulticast({
        tokens: rows.map((linha) => linha.fcm_token),
        data: { tipo: 'novidade' },
        android: { priority: 'high' },
      })
      .catch((erro) => {
        console.error('[push] falha ao enviar', erro.message);
        return null;
      });
    if (!resposta) return;

    // Token que o Google ja descartou: limpa, senao tentariamos pra sempre.
    const mortos = resposta.responses
      .map((r, i) => (!r.success && CODIGOS_TOKEN_MORTO.has(r.error?.code) ? rows[i].id : null))
      .filter(Boolean);
    if (mortos.length > 0) {
      await pool.query('UPDATE dispositivos SET fcm_token = NULL WHERE id = ANY($1)', [mortos]).catch(() => {});
    }
  }

  return { enviarParaCasa };
}
