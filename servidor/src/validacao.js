/** Validacoes simples e explicitas, para nao carregar dependencia de schema. */

export function texto(valor, { max = 200, obrigatorio = false } = {}) {
  if (valor === undefined || valor === null) {
    if (obrigatorio) throw new ErroDeEntrada('campo de texto obrigatorio ausente');
    return null;
  }
  if (typeof valor !== 'string') throw new ErroDeEntrada('esperava texto');
  const limpo = valor.trim();
  if (obrigatorio && limpo === '') throw new ErroDeEntrada('texto vazio');
  if (limpo.length > max) throw new ErroDeEntrada(`texto acima de ${max} caracteres`);
  return limpo === '' ? null : limpo;
}

export function inteiro(valor, { min = -1_000_000, max = 1_000_000, padrao = 0 } = {}) {
  if (valor === undefined || valor === null) return padrao;
  if (!Number.isInteger(valor)) throw new ErroDeEntrada('esperava numero inteiro');
  if (valor < min || valor > max) throw new ErroDeEntrada('numero fora do intervalo aceito');
  return valor;
}

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function uuid(valor) {
  if (typeof valor !== 'string' || !UUID.test(valor)) throw new ErroDeEntrada('id invalido');
  return valor.toLowerCase();
}

/**
 * Data vinda do celular. Relogio errado e comum, entao datas muito no futuro
 * viram "agora" em vez de bagunçar a ordenacao do historico.
 */
export function dataDoCliente(valor) {
  if (!valor) return new Date();
  const data = new Date(valor);
  if (Number.isNaN(data.getTime())) throw new ErroDeEntrada('data invalida');
  const limiteFuturo = Date.now() + 24 * 60 * 60 * 1000;
  return data.getTime() > limiteFuturo ? new Date() : data;
}

export function lista(valor, { max = 1000 } = {}) {
  if (valor === undefined || valor === null) return [];
  if (!Array.isArray(valor)) throw new ErroDeEntrada('esperava uma lista');
  if (valor.length > max) throw new ErroDeEntrada(`lista acima de ${max} itens`);
  return valor;
}

export class ErroDeEntrada extends Error {
  constructor(mensagem) {
    super(mensagem);
    this.status = 400;
  }
}
