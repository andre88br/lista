#!/usr/bin/env bash
# Tenta criar a maquina ARM gratuita (VM.Standard.A1.Flex) repetidamente, ate
# conseguir. As maquinas ARM do plano gratuito vivem lotadas, e a capacidade
# libera em janelas curtas e imprevisiveis - insistir e o que funciona.
#
# RODE ESTE SCRIPT NO ORACLE CLOUD SHELL (o terminal do proprio painel da
# Oracle, icone >_ no canto superior direito). La o comando `oci` ja vem
# autenticado como voce, sem precisar configurar nada.
#
#   bash tentar-a1.sh
#
# Pode deixar rodando. Ctrl+C para parar.
set -uo pipefail

OCPUS="${OCPUS:-1}"
MEMORIA_GB="${MEMORIA_GB:-6}"
NOME="${NOME:-lista-compras}"
ESPERA_SEGUNDOS="${ESPERA_SEGUNDOS:-60}"

verde()   { printf '\033[32m%s\033[0m\n' "$*"; }
amarelo() { printf '\033[33m%s\033[0m\n' "$*"; }
vermelho(){ printf '\033[31m%s\033[0m\n' "$*"; }

command -v oci >/dev/null 2>&1 || {
  vermelho "O comando 'oci' nao existe aqui."
  echo "Rode este script no Oracle Cloud Shell: painel da Oracle, icone >_ no topo direito."
  exit 1
}

# ------------------------------------------------------------------ descobrir
COMPARTIMENTO="${COMPARTIMENTO:-${OCI_TENANCY:-}}"
if [ -z "$COMPARTIMENTO" ]; then
  vermelho "Nao consegui descobrir seu compartimento."
  echo "Pegue o OCID da tenancy em: Perfil (canto superior direito) > Tenancy > OCID"
  echo "e rode:  COMPARTIMENTO=ocid1.tenancy.oc1..xxxxx bash tentar-a1.sh"
  exit 1
fi

echo "Compartimento: $COMPARTIMENTO"
echo "Configuracao:  $OCPUS OCPU / $MEMORIA_GB GB"

echo "Procurando a subnet publica..."
SUBNET=$(oci network subnet list --compartment-id "$COMPARTIMENTO" --all \
  --query 'data[?"prohibit-public-ip-on-vnic"==`false`]|[0].id' --raw-output 2>/dev/null)
if [ -z "${SUBNET:-}" ] || [ "$SUBNET" = "null" ]; then
  vermelho "Nenhuma subnet publica encontrada."
  echo "Crie a rede primeiro: Networking > Virtual Cloud Networks > Start VCN Wizard"
  echo "> Create VCN with Internet Connectivity. Depois rode este script de novo."
  exit 1
fi
echo "Subnet: $SUBNET"

echo "Procurando a imagem do Ubuntu 24.04 para ARM..."
IMAGEM=$(oci compute image list --compartment-id "$COMPARTIMENTO" \
  --operating-system "Canonical Ubuntu" --operating-system-version "24.04" \
  --shape "VM.Standard.A1.Flex" --sort-by TIMECREATED --sort-order DESC \
  --query 'data[0].id' --raw-output 2>/dev/null)
if [ -z "${IMAGEM:-}" ] || [ "$IMAGEM" = "null" ]; then
  vermelho "Nao achei a imagem do Ubuntu 24.04 para ARM."
  exit 1
fi
echo "Imagem: $IMAGEM"

CHAVE="$HOME/.ssh/id_lista"
if [ ! -f "$CHAVE.pub" ]; then
  echo "Gerando uma chave SSH nova em $CHAVE ..."
  ssh-keygen -t ed25519 -N "" -f "$CHAVE" >/dev/null
fi
CHAVE_PUBLICA=$(cat "$CHAVE.pub")

METADADOS=$(python3 -c "
import json, sys
print(json.dumps({'ssh_authorized_keys': sys.argv[1]}))
" "$CHAVE_PUBLICA")

mapfile -t DOMINIOS < <(oci iam availability-domain list --compartment-id "$COMPARTIMENTO" \
  --query 'data[].name' --raw-output 2>/dev/null | tr -d '[]", ' | grep -v '^$')
if [ "${#DOMINIOS[@]}" -eq 0 ]; then
  vermelho "Nao consegui listar os availability domains."
  exit 1
fi
echo "Availability domains: ${DOMINIOS[*]}"
echo

# ------------------------------------------------------------------- insistir
verde "Tentando criar a maquina. Pode deixar rodando; Ctrl+C para parar."
echo

TENTATIVA=0
while true; do
  TENTATIVA=$((TENTATIVA + 1))
  for AD in "${DOMINIOS[@]}"; do
    printf '[%s] tentativa %d em %s ... ' "$(date +%H:%M:%S)" "$TENTATIVA" "$AD"

    SAIDA=$(oci compute instance launch \
      --availability-domain "$AD" \
      --compartment-id "$COMPARTIMENTO" \
      --shape "VM.Standard.A1.Flex" \
      --shape-config "{\"ocpus\":$OCPUS,\"memoryInGBs\":$MEMORIA_GB}" \
      --image-id "$IMAGEM" \
      --subnet-id "$SUBNET" \
      --display-name "$NOME" \
      --assign-public-ip true \
      --metadata "$METADADOS" \
      --wait-for-state RUNNING 2>&1)
    CODIGO=$?

    if [ $CODIGO -eq 0 ]; then
      echo
      verde "Conseguiu! A maquina foi criada."
      INSTANCIA=$(echo "$SAIDA" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['id'])" 2>/dev/null)
      if [ -n "${INSTANCIA:-}" ]; then
        IP=$(oci compute instance list-vnics --instance-id "$INSTANCIA" \
          --query 'data[0]."public-ip"' --raw-output 2>/dev/null)
        echo
        verde "IP publico: $IP"
        echo "Chave privada: $CHAVE"
        echo
        echo "Baixe a chave para o seu computador (no Cloud Shell: menu do terminal > Download)"
        echo "e entre com:"
        echo "  ssh -i $CHAVE ubuntu@$IP"
      fi
      exit 0
    fi

    if echo "$SAIDA" | grep -qi "out of capacity\|OutOfCapacity\|InternalError"; then
      amarelo "sem vaga"
    elif echo "$SAIDA" | grep -qi "LimitExceeded\|limit for this resource"; then
      echo
      vermelho "Voce ja atingiu o limite gratuito de maquinas ARM."
      echo "Apague alguma instancia antiga em Compute > Instances e tente de novo."
      exit 1
    else
      echo
      vermelho "Erro diferente de falta de capacidade:"
      echo "$SAIDA" | tail -20
      exit 1
    fi
  done
  sleep "$ESPERA_SEGUNDOS"
done
