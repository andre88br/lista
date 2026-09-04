# 3. Instalar o servidor no VPS

Pré-requisitos: o [VPS criado](01-criar-vps-oracle.md) e o [subdomínio do DuckDNS](02-duckdns.md) apontando para o IP dele.

## 3.1 Baixar e instalar

Conectado ao VPS por SSH:

```bash
sudo apt update && sudo apt install -y git
git clone -b claude/shopping-list-barcode-app-gf9cmb https://github.com/andre88br/lista.git
cd lista/servidor
./instalar.sh
```

O instalador vai:

1. conferir memória e arquitetura da máquina;
2. instalar o Docker, se não houver;
3. perguntar o **domínio** e o **token do DuckDNS** e gerar uma senha aleatória para o banco (grava tudo em `.env`, que fica só na máquina);
4. liberar as portas 80/443 no iptables;
5. agendar a atualização do IP no DuckDNS;
6. subir os três contêineres e conferir se o HTTPS respondeu.

No fim ele imprime o endereço para colocar no app, algo como `https://andre-lista.duckdns.org`.

> **A primeira execução demora** — Docker, imagens e certificado. Entre 5 e 10 minutos é normal.

## 3.2 Conferir que está no ar

Do seu computador (não do VPS):

```bash
curl https://andre-lista.duckdns.org/saude
```

Resposta esperada:

```json
{"ok":true,"servico":"lista","agora":"2026-09-04T02:31:00.000Z"}
```

Se responder com o cadeado válido, está pronto para configurar no app: **Ajustes → Compartilhar com outra pessoa**.

## 3.3 Quando algo não responde

Os problemas são quase sempre um destes três, nesta ordem:

| Sintoma | Causa provável | O que fazer |
|---|---|---|
| `curl` trava sem resposta | Security List da Oracle bloqueando | Painel → Subnet → Security List → liberar TCP 80 e 443 (seção 1.4 do guia 1) |
| `Connection refused` | contêiner fora do ar | `sudo docker compose ps` e `sudo docker compose logs -n 50` |
| Erro de certificado | domínio não aponta para o IP | `ping seu-dominio.duckdns.org` tem que devolver o IP do VPS |

Comandos do dia a dia (sempre dentro de `lista/servidor`):

```bash
sudo docker compose ps               # estado dos contêineres
sudo docker compose logs -f api      # acompanhar o servidor
sudo docker compose logs -f caddy    # problemas de certificado/HTTPS
sudo docker compose restart          # reiniciar tudo
```

## 3.4 Atualizar quando eu mexer no código

```bash
cd ~/lista
git pull
cd servidor
sudo docker compose up -d --build
```

As alterações no banco são aplicadas sozinhas quando o servidor sobe.

## 3.5 Backup

```bash
cd ~/lista/servidor
./backup.sh
```

Guarda um `.sql.gz` em `servidor/backups/` e apaga os com mais de 14 dias. Para rodar todo dia às 3h:

```bash
(crontab -l 2>/dev/null; echo "0 3 * * * $HOME/lista/servidor/backup.sh >> $HOME/lista/servidor/backups/backup.log 2>&1") | crontab -
```

Para restaurar:

```bash
gunzip -c backups/lista-20260904-0300.sql.gz | sudo docker compose exec -T banco psql -U lista lista
```

## 3.6 O que o servidor guarda

Só o necessário para o app funcionar: um identificador anônimo de cada aparelho (o token vai gravado apenas como hash), a casa e seu código, os nomes dos produtos e o histórico de leituras. **Sem nome, e-mail, telefone ou localização.** O acesso é sempre pelo token do aparelho, e cada consulta é filtrada pela casa dele — um aparelho nunca enxerga dados de outra casa (tem teste automatizado cobrindo exatamente isso).
