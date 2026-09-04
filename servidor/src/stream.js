/**
 * Avisos em tempo real por SSE: quando um celular envia leituras, o outro
 * recebe um "novidade" e sincroniza na hora, em vez de esperar o proximo ciclo.
 */
export function criarAvisador() {
  const porCasa = new Map();

  function inscrever(casaId, res) {
    if (!porCasa.has(casaId)) porCasa.set(casaId, new Set());
    porCasa.get(casaId).add(res);
    return () => {
      const conjunto = porCasa.get(casaId);
      if (!conjunto) return;
      conjunto.delete(res);
      if (conjunto.size === 0) porCasa.delete(casaId);
    };
  }

  function avisar(casaId, dados = {}) {
    const conjunto = porCasa.get(casaId);
    if (!conjunto) return 0;
    const linha = `event: novidade\ndata: ${JSON.stringify(dados)}\n\n`;
    for (const res of conjunto) {
      try {
        res.write(linha);
      } catch {
        conjunto.delete(res);
      }
    }
    return conjunto.size;
  }

  function inscritos(casaId) {
    return porCasa.get(casaId)?.size ?? 0;
  }

  return { inscrever, avisar, inscritos };
}
