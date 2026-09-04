import { randomBytes, randomUUID, createHash } from 'node:crypto';

// Alfabeto sem 0/O e 1/I/L: o codigo vai ser lido em voz alta e digitado na mao.
const ALFABETO = '23456789ABCDEFGHJKMNPQRSTUVWXYZ';

/** Codigo de convite com ~40 bits de entropia, no formato ABCD-EFGH. */
export function gerarCodigoConvite() {
  const bytes = randomBytes(8);
  let bruto = '';
  for (let i = 0; i < 8; i++) bruto += ALFABETO[bytes[i] % ALFABETO.length];
  return `${bruto.slice(0, 4)}-${bruto.slice(4)}`;
}

/** Aceita o codigo digitado de qualquer jeito: minusculo, sem hifen, com espacos. */
export function normalizarCodigo(entrada) {
  if (typeof entrada !== 'string') return null;
  const limpo = entrada.toUpperCase().replace(/[^0-9A-Z]/g, '');
  if (limpo.length !== 8) return null;
  if ([...limpo].some((c) => !ALFABETO.includes(c))) return null;
  return `${limpo.slice(0, 4)}-${limpo.slice(4)}`;
}

/** Token do aparelho: 32 bytes aleatorios. So o hash e guardado no servidor. */
export function gerarToken() {
  return randomBytes(32).toString('base64url');
}

export function hashDoToken(token) {
  return createHash('sha256').update(token).digest('hex');
}

export { randomUUID };
