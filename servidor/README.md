# Servidor de sincronização

API que permite duas pessoas compartilharem a mesma lista, estoque e comprados. Roda no seu próprio VPS.

- **Instalação:** [docs/03-instalar-servidor.md](../docs/03-instalar-servidor.md)
- **Node 22 + Express + Postgres**, três contêineres (api, banco, caddy), HTTPS automático.

## Identidade

Quem participa da casa é a **pessoa**, não o aparelho. O app faz login com Google e manda o token para `/v1/auth/google`; o servidor confere a assinatura contra as chaves públicas do Google e exige que o token tenha sido emitido para este aplicativo (o `aud` precisa bater com o nosso Client ID) — sem essa segunda checagem, um token válido de qualquer outro app seria aceito.

Disso decorrem as três coisas que o login resolve: trocar de celular mantém a casa (a participação segue a pessoa), entrar num aparelho novo não pede código, e cada leitura registra quem escaneou.

Cada instalação recebe seu próprio token de aparelho, então sair num celular não derruba o outro.

## Como a sincronização funciona

Cada leitura de código de barras vira um **evento com deltas** (`+1 na lista`, `−1 no estoque`), com um id gerado no celular. A quantidade de um produto é a **soma dos deltas**.

Isso resolve os dois problemas difíceis de uma vez:

- **Soma não depende de ordem**, então dois celulares offline escaneando o mesmo produto chegam ao mesmo número quando voltam a ter sinal.
- **Reenviar não duplica**, porque o id vem do celular (`ON CONFLICT (id) DO NOTHING`). Sincronizar duas vezes é inofensivo — e é isso que permite reenviar os últimos minutos por segurança.

As somas guardadas são **cruas** (podem ficar negativas); quem limita em zero é a exibição no app. Se limitasse na hora de gravar, dois celulares poderiam divergir dependendo da ordem em que os eventos chegassem.

Nome e marca de produto não são contador: aí vence a escrita mais recente (`atualizado_em`).

## Rotas

| Método | Rota | Para quê |
|---|---|---|
| POST | `/v1/auth/google` | login com Google: devolve o token do aparelho e a casa (única rota sem token) |
| POST | `/v1/casas` | cria a casa e devolve o código de convite |
| POST | `/v1/casas/entrar` | entra numa casa pelo código |
| GET | `/v1/casas/atual` | dados da casa e quantos membros |
| POST | `/v1/casas/codigo` | gera um código novo |
| DELETE | `/v1/casas/atual` | sai da casa (não apaga nada dos outros) |
| POST | `/v1/sync` | envia eventos e produtos |
| GET | `/v1/sync?desde=<seq>` | busca o que é novo |
| GET | `/v1/sync/instantaneo` | estado completo, para quem acabou de entrar |
| GET | `/v1/stream` | avisos em tempo real (SSE) |
| GET | `/saude` | healthcheck |

## Desenvolvimento

```bash
npm install
# precisa de um Postgres acessível
DATABASE_URL=postgres://postgres@127.0.0.1:5432/postgres npm test
```

Os testes sobem o servidor de verdade contra um Postgres de verdade, cada um em seu próprio schema. Cobrem entrada na casa pelo código, idempotência, cursor incremental, avisos em tempo real e — o mais importante — que um aparelho de outra casa não enxerga absolutamente nada.
