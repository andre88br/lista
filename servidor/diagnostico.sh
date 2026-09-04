#!/usr/bin/env bash
# Diagnostico do VPS: so LE informacoes, nao muda nada na maquina.
# Uso: bash diagnostico.sh

set -u

titulo() { printf '\n\033[1m== %s ==\033[0m\n' "$1"; }
tem() { command -v "$1" >/dev/null 2>&1; }

titulo "Sistema"
if [ -r /etc/os-release ]; then . /etc/os-release; echo "SO:          ${PRETTY_NAME:-desconhecido}"; fi
echo "Kernel:      $(uname -r)"
echo "Arquitetura: $(uname -m)   (aarch64 = Ampere ARM, x86_64 = AMD)"
echo "Uptime:      $(uptime -p 2>/dev/null || true)"

titulo "Memoria e disco"
free -h 2>/dev/null | sed 's/^/  /'
echo
df -h / 2>/dev/null | sed 's/^/  /'
echo
echo "CPUs: $(nproc 2>/dev/null || echo '?')"
if [ -z "$(swapon --show 2>/dev/null)" ]; then
  echo "Swap:  nao configurado"
else
  echo "Swap:  configurado"
fi

titulo "Docker"
if tem docker; then
  docker --version
  if docker compose version >/dev/null 2>&1; then
    docker compose version
  else
    echo "docker compose (plugin v2): NAO encontrado"
  fi
  if docker info >/dev/null 2>&1; then
    echo "Permissao para usar o docker sem sudo: sim"
  else
    echo "Permissao para usar o docker sem sudo: nao (precisa de sudo ou do grupo docker)"
  fi
  echo "Conteineres em execucao:"
  docker ps --format '  {{.Names}}  {{.Image}}  {{.Status}}  {{.Ports}}' 2>/dev/null || echo "  (sem permissao para listar)"
else
  echo "Docker NAO instalado (o instalador cuida disso)"
fi

titulo "Portas em uso"
if tem ss; then
  ss -ltnp 2>/dev/null | sed 's/^/  /'
elif tem netstat; then
  netstat -ltnp 2>/dev/null | sed 's/^/  /'
else
  echo "  (nem ss nem netstat disponiveis)"
fi

titulo "Firewall dentro da maquina (iptables)"
if tem iptables; then
  REGRAS=$(sudo iptables -S INPUT 2>/dev/null)
  if [ -z "$REGRAS" ]; then
    echo "  (precisa de sudo para listar as regras)"
  else
    echo "Regras de entrada (INPUT):"
    echo "$REGRAS" | sed 's/^/  /'
    POLITICA=$(echo "$REGRAS" | awk '/^-P INPUT/ {print $3}')
    BLOQUEIOS=$(echo "$REGRAS" | grep -c -E -- '-j (DROP|REJECT)')
    if [ "$POLITICA" = "ACCEPT" ] && [ "$BLOQUEIOS" -eq 0 ]; then
      echo "Nada bloqueado no iptables (politica ACCEPT, sem regras de DROP/REJECT): portas 80 e 443 ja liberadas aqui dentro."
    else
      for porta in 80 443; do
        if echo "$REGRAS" | grep -q -- "--dport $porta -j ACCEPT"; then
          echo "Porta $porta liberada no iptables: SIM"
        else
          echo "Porta $porta liberada no iptables: NAO  <-- precisa liberar (veja docs/01-criar-vps-oracle.md, secao 1.4)"
        fi
      done
    fi
  fi
else
  echo "iptables nao encontrado"
fi
if tem ufw; then echo "ufw: $(sudo ufw status 2>/dev/null | head -1)"; fi

titulo "Rede"
echo -n "IP publico visto de fora: "
curl -s --max-time 8 https://api.ipify.org || echo "(nao consegui consultar)"
echo
echo "IPs locais:"
ip -4 -o addr show scope global 2>/dev/null | awk '{print "  " $2 ": " $4}'

titulo "Portas 80/443 acessiveis de fora"
IP_PUB=$(curl -s --max-time 8 https://api.ipify.org 2>/dev/null)
if [ -n "${IP_PUB:-}" ]; then
  for porta in 80 443; do
    if curl -s --max-time 6 -o /dev/null "http://$IP_PUB:$porta" 2>/dev/null; then
      echo "  Porta $porta: alguma coisa respondeu"
    else
      echo "  Porta $porta: sem resposta (normal se ainda nao instalamos nada)"
    fi
  done
fi

titulo "Resumo"
echo "Mande esta saida inteira no chat para eu ajustar a instalacao."
