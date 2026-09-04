#!/usr/bin/env bash
# Instala e sobe o servidor do app Lista & Estoque neste VPS.
# Pode ser rodado de novo a qualquer momento: ele nao refaz o que ja esta feito.
set -euo pipefail

vermelho() { printf '\033[31m%s\033[0m\n' "$*"; }
verde()    { printf '\033[32m%s\033[0m\n' "$*"; }
amarelo()  { printf '\033[33m%s\033[0m\n' "$*"; }
titulo()   { printf '\n\033[1m== %s ==\033[0m\n' "$*"; }

AQUI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$AQUI"

if [ "$(id -u)" -eq 0 ]; then
  SUDO=""
elif command -v sudo >/dev/null 2>&1; then
  SUDO="sudo"
else
  vermelho "Este usuario nao e root e o comando sudo nao existe nesta maquina."
  echo "Entre como root (sudo su -) ou instale o sudo, e rode o script de novo."
  exit 1
fi

titulo "1/7 Conferindo o sistema"
echo "Arquitetura: $(uname -m)"
MEM_MB=$(free -m | awk '/^Mem:/ {print $2}')
echo "Memoria: ${MEM_MB} MB"

POUCA_MEMORIA=0
if [ "$MEM_MB" -lt 1500 ]; then
  POUCA_MEMORIA=1
  amarelo "Maquina pequena: vou usar a configuracao enxuta e criar swap."
fi

titulo "2/7 Swap"
# Em maquinas de 1 GB, sem swap o sistema mata processos durante o build.
criar_swap() {
  $SUDO fallocate -l 2G /swapfile 2>/dev/null || $SUDO dd if=/dev/zero of=/swapfile bs=1M count=2048 status=none
  $SUDO chmod 600 /swapfile
  $SUDO mkswap /swapfile >/dev/null
  $SUDO swapon /swapfile
  grep -q '^/swapfile' /etc/fstab 2>/dev/null || echo '/swapfile none swap sw 0 0' | $SUDO tee -a /etc/fstab >/dev/null
}

if [ "$POUCA_MEMORIA" -eq 1 ] && [ -z "$(swapon --show 2>/dev/null)" ]; then
  echo "Criando 2 GB de swap..."
  # Swap ajuda muito no build, mas nao vale abortar a instalacao se falhar.
  if criar_swap; then
    verde "Swap criado."
  else
    amarelo "Nao consegui criar swap. Sigo assim mesmo; se o build morrer por falta"
    amarelo "de memoria, me avise para gerar a imagem fora da maquina."
  fi
elif [ "$POUCA_MEMORIA" -eq 1 ]; then
  verde "Swap ja existe."
else
  echo "Memoria suficiente; swap dispensavel."
fi

titulo "3/7 Docker"
if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
  verde "Docker ja instalado: $(docker --version)"
else
  echo "Instalando o Docker (pode levar alguns minutos)..."
  curl -fsSL https://get.docker.com | $SUDO sh
  $SUDO usermod -aG docker "$USER" || true
  verde "Docker instalado. (Para usar sem sudo, saia e entre de novo no SSH.)"
fi
DOCKER="$SUDO docker"

titulo "4/7 Configuracao"
if [ -f .env ]; then
  verde "Arquivo .env ja existe; mantendo o que esta la."
  # shellcheck disable=SC1091
  . ./.env
else
  # Tudo pode vir por variavel de ambiente, para rodar sem terminal interativo:
  #   DOMINIO=xxx.duckdns.org DUCKDNS_TOKEN=yyy ./instalar.sh
  DOMINIO="${DOMINIO:-}"
  while [ -z "$DOMINIO" ]; do
    if [ ! -t 0 ]; then
      vermelho "Sem terminal interativo e sem a variavel DOMINIO."
      echo "Rode assim:  DOMINIO=seu-dominio.duckdns.org ./instalar.sh"
      exit 1
    fi
    read -rp "Endereco do servidor (ex.: andre-lista.duckdns.org): " DOMINIO
  done

  if [ -z "${DUCKDNS_TOKEN:-}" ] && [ -t 0 ]; then
    read -rp "Token do DuckDNS (opcional, Enter para pular): " DUCKDNS_TOKEN
  fi
  # Sem o "|| true": head fecha o pipe cedo, tr leva SIGPIPE e o pipefail derruba tudo.
  POSTGRES_PASSWORD="$(openssl rand -base64 48 | tr -d '/+=' | cut -c1-32 || true)"
  [ -n "$POSTGRES_PASSWORD" ] || POSTGRES_PASSWORD="$(date +%s%N | sha256sum | cut -c1-32)"

  cat > .env <<EOF
DOMINIO=$DOMINIO
POSTGRES_PASSWORD=$POSTGRES_PASSWORD
DUCKDNS_TOKEN=${DUCKDNS_TOKEN:-}
EOF
  # COMPOSE_FILE no .env faz todo "docker compose" seguinte usar os mesmos
  # arquivos, inclusive quando voce rodar na mao depois.
  if [ "$POUCA_MEMORIA" -eq 1 ]; then
    echo "COMPOSE_FILE=docker-compose.yml:docker-compose.pouca-memoria.yml" >> .env
  fi
  chmod 600 .env
  verde "Arquivo .env criado (senha do banco gerada automaticamente)."
fi

titulo "5/7 Portas 80 e 443"
# A Oracle tem dois firewalls. Este script cuida do de dentro da maquina;
# o do painel (Security List da VCN) so voce consegue liberar, pela web.
if command -v iptables >/dev/null 2>&1; then
  for porta in 80 443; do
    if $SUDO iptables -C INPUT -p tcp --dport "$porta" -j ACCEPT 2>/dev/null; then
      echo "Porta $porta ja liberada no iptables."
    else
      $SUDO iptables -I INPUT 6 -m state --state NEW -p tcp --dport "$porta" -j ACCEPT 2>/dev/null \
        || $SUDO iptables -I INPUT -p tcp --dport "$porta" -j ACCEPT
      echo "Porta $porta liberada no iptables."
    fi
  done
  $SUDO netfilter-persistent save >/dev/null 2>&1 || $SUDO sh -c 'iptables-save > /etc/iptables/rules.v4' 2>/dev/null || true
fi
echo "Lembrete: no painel da Oracle, a Security List da sub-rede tambem precisa liberar 80 e 443."

titulo "6/7 DuckDNS"
# shellcheck disable=SC1091
. ./.env
if [ -n "${DUCKDNS_TOKEN:-}" ]; then
  SUB="${DOMINIO%%.duckdns.org}"
  LINHA="*/15 * * * * curl -fsS 'https://www.duckdns.org/update?domains=$SUB&token=$DUCKDNS_TOKEN&ip=' >/dev/null 2>&1"

  # Numa maquina nova nao existe crontab ainda, e "crontab -l" sai com erro.
  # Sem os "|| true", isso derrubaria o script inteiro por causa do set -e.
  if command -v crontab >/dev/null 2>&1; then
    CRON_ATUAL="$(crontab -l 2>/dev/null || true)"
    CRON_LIMPO="$(printf '%s\n' "$CRON_ATUAL" | grep -v 'duckdns.org/update' || true)"
    if printf '%s\n%s\n' "$CRON_LIMPO" "$LINHA" | grep -v '^$' | crontab - 2>/dev/null; then
      echo "Atualizacao de IP agendada a cada 15 minutos."
    else
      amarelo "Nao consegui agendar no cron; o IP nao sera atualizado sozinho."
    fi
  else
    amarelo "Sem cron nesta maquina; o IP nao sera atualizado sozinho."
  fi

  if curl -fsS "https://www.duckdns.org/update?domains=$SUB&token=$DUCKDNS_TOKEN&ip=" >/dev/null 2>&1; then
    verde "IP atualizado no DuckDNS."
  else
    amarelo "Nao consegui falar com o DuckDNS agora. Confira o token e o dominio."
  fi
else
  echo "Sem token do DuckDNS; pulando a atualizacao automatica de IP."
fi

titulo "7/7 Subindo os conteineres"
$DOCKER compose up -d --build

echo "Esperando o servidor responder..."
OK=0
for _ in $(seq 1 30); do
  if curl -fsS "http://127.0.0.1:8080/saude" >/dev/null 2>&1 || $DOCKER compose exec -T api node -e "fetch('http://127.0.0.1:8080/saude').then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))" >/dev/null 2>&1; then
    OK=1; break
  fi
  sleep 2
done

if [ "$OK" -eq 1 ]; then
  verde "Servidor no ar."
else
  vermelho "O servidor nao respondeu. Veja os logs com:  $SUDO docker compose logs -n 50"
  exit 1
fi

titulo "Conferindo o HTTPS de fora"
echo "O certificado leva alguns segundos na primeira vez."
sleep 10
if curl -fsS --max-time 20 "https://$DOMINIO/saude"; then
  echo
  verde "Tudo pronto! No app, use este endereco: https://$DOMINIO"
else
  echo
  vermelho "Ainda nao respondeu pelo dominio. Verifique, nesta ordem:"
  echo "  1. O DuckDNS aponta para o IP publico desta maquina?"
  echo "  2. A Security List da VCN na Oracle libera 80 e 443?"
  echo "  3. Logs do Caddy:  $SUDO docker compose logs caddy -n 50"
fi
