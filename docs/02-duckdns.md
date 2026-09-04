# 2. Um endereço grátis para o servidor (DuckDNS)

O Android bloqueia conexões sem criptografia. Para ter HTTPS de verdade (cadeado válido, certificado grátis do Let's Encrypt), o servidor precisa de um **nome**, não só de um IP. O DuckDNS dá um subdomínio grátis em um minuto.

## 2.1 Criar o subdomínio

1. Acesse <https://www.duckdns.org> e entre com Google, GitHub ou Twitter (não precisa criar senha).
2. No campo do topo, digite um nome — por exemplo `andre-lista` — e clique em **add domain**.
   - Seu endereço será `andre-lista.duckdns.org`.
3. Na linha do domínio criado, cole o **IP público do VPS** no campo *current ip* e clique em **update ip**.
4. Guarde o **token** que aparece no topo da página (parece `a1b2c3d4-...`). Ele serve para o servidor atualizar o IP sozinho.

## 2.2 Conferir se o nome já responde

No seu computador:

```bash
ping -c 2 andre-lista.duckdns.org
```

Tem que responder com o IP do VPS. Se ainda não responder, espere 1–2 minutos e tente de novo.

## 2.3 Manter o IP atualizado (recomendado)

O IP público da Oracle é fixo enquanto a instância existir, então isso é mais uma proteção do que uma necessidade. O script de instalação do próximo passo já configura essa atualização automática se você informar o token — não precisa fazer nada agora.

---

Anote as duas informações, que o instalador vai pedir:

- **Domínio**: `algumacoisa.duckdns.org`
- **Token do DuckDNS**: `a1b2c3d4-...`

Próximo passo: **[3. Instalar o servidor](03-instalar-servidor.md)**.
