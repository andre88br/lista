#!/usr/bin/env bash
# Backup do banco. Guarda os ultimos 14 dias em backups/.
set -euo pipefail

AQUI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$AQUI"
mkdir -p backups

if [ "$(id -u)" -eq 0 ]; then SUDO=""; else SUDO="sudo"; fi
ARQUIVO="backups/lista-$(date +%Y%m%d-%H%M).sql.gz"

$SUDO docker compose exec -T banco pg_dump -U lista lista | gzip > "$ARQUIVO"
echo "Backup salvo em $ARQUIVO ($(du -h "$ARQUIVO" | cut -f1))"

find backups -name 'lista-*.sql.gz' -mtime +14 -delete
echo "Backups mantidos: $(ls -1 backups | wc -l)"

# Para rodar todo dia as 3h da manha:
#   (crontab -l 2>/dev/null; echo "0 3 * * * $AQUI/backup.sh >> $AQUI/backups/backup.log 2>&1") | crontab -
