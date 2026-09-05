import { OAuth2Client } from 'google-auth-library';

/**
 * Confere o token de login que o celular recebeu do Google.
 *
 * O que importa aqui: o token e assinado pelo Google, e a biblioteca verifica a
 * assinatura contra as chaves publicas dele. Alem disso, exigimos que o token
 * tenha sido emitido para *este* aplicativo (o `aud` precisa ser o nosso client
 * ID) - sem essa checagem, um token valido de qualquer outro app seria aceito.
 */
export function criarVerificadorGoogle(clientId) {
  if (!clientId) {
    return async () => {
      throw Object.assign(new Error('login com Google nao configurado neste servidor'), { status: 503 });
    };
  }

  const cliente = new OAuth2Client(clientId);

  return async function verificar(idToken) {
    let bilhete;
    try {
      bilhete = await cliente.verifyIdToken({ idToken, audience: clientId });
    } catch (erro) {
      throw Object.assign(new Error('login do Google invalido ou expirado'), { status: 401, causa: erro.message });
    }

    const dados = bilhete.getPayload();
    if (!dados?.sub) {
      throw Object.assign(new Error('login do Google sem identificacao'), { status: 401 });
    }
    // Conta de Google sem e-mail confirmado nao serve para identificar ninguem.
    if (dados.email && dados.email_verified === false) {
      throw Object.assign(new Error('e-mail do Google nao verificado'), { status: 401 });
    }

    return {
      sub: dados.sub,
      email: dados.email ?? null,
      nome: dados.name || dados.given_name || dados.email?.split('@')[0] || 'Alguem',
      fotoUrl: dados.picture ?? null,
    };
  };
}
