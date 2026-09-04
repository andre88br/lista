/**
 * Limitador de tentativas em memoria. O servidor roda numa instancia so,
 * entao nao precisa de Redis: um Map com janela deslizante resolve.
 */
export function criarLimitador({ maximo, janelaMs }) {
  const tentativas = new Map();

  return function permitir(chave) {
    const agora = Date.now();
    const registros = (tentativas.get(chave) || []).filter((t) => agora - t < janelaMs);
    if (registros.length >= maximo) {
      tentativas.set(chave, registros);
      return false;
    }
    registros.push(agora);
    tentativas.set(chave, registros);
    return true;
  };
}

/** Remove chaves velhas de tempos em tempos para o Map nao crescer sem limite. */
export function limparPeriodicamente(limitadores, intervaloMs = 10 * 60 * 1000) {
  const timer = setInterval(() => {
    for (const limpar of limitadores) limpar();
  }, intervaloMs);
  timer.unref?.();
  return timer;
}
