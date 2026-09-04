# 1. Criar o servidor (VPS) na Oracle Cloud

O plano gratuito da Oracle ("Always Free") dá uma máquina bem mais forte do que este app precisa, sem cobrança e sem prazo para acabar. Este guia vai do zero até você conseguir entrar na máquina pelo terminal.

Tempo: ~30 minutos, sendo boa parte esperando a verificação da conta.

---

## 1.1 Criar a conta

1. Acesse <https://www.oracle.com/cloud/free/> e clique em **Start for free**.
2. Preencha os dados. **Atenção na escolha do "Home Region"**: escolha **Brazil East (São Paulo)** — essa escolha **não pode ser mudada depois**, e a região define de onde o app vai responder. (Se São Paulo não aparecer, "Brazil Southeast (Vinhedo)" também serve.)
3. A Oracle pede **cartão de crédito** apenas para verificar identidade. É feita uma cobrança de teste de aproximadamente US$ 1,00 que é estornada. Enquanto sua conta estiver como "Always Free", nada é cobrado.
4. A aprovação costuma levar de 5 minutos a algumas horas. Você recebe um e-mail quando estiver liberada.

> Se a conta for recusada (acontece com alguns cartões), tente outro cartão de crédito — cartões pré-pagos e alguns virtuais costumam ser rejeitados.

---

## 1.2 Criar a máquina

No painel (<https://cloud.oracle.com>):

1. Menu ☰ → **Compute** → **Instances** → botão **Create instance**.
2. **Name**: `lista-compras` (qualquer nome serve).
3. **Image and shape** → **Edit**:
   - **Image**: clique em *Change image* e escolha **Canonical Ubuntu 24.04**.
   - **Shape**: clique em *Change shape* → aba **Ampere** → **VM.Standard.A1.Flex**.
     - **OCPUs**: `2` · **Memory**: `12 GB`.
     - (O limite gratuito é 4 OCPUs e 24 GB no total. Usar 2 e 12 deixa folga para você criar outra máquina depois, e sobra muito para este app.)
4. **Primary VNIC / Networking**: deixe como vem (ele cria uma rede nova). Só confirme que **Assign a public IPv4 address** está marcado como **Yes**.
5. **Add SSH keys**: escolha **Generate a key pair for me** e clique em **Save private key** — guarde esse arquivo (`ssh-key-....key`), é a única forma de entrar na máquina. Salve também a chave pública.
6. **Create**.

Em 1–2 minutos a instância fica **RUNNING**. Anote o **Public IP address** que aparece na página — vamos usar em tudo daqui para frente.

### Se aparecer "Out of capacity"

É o erro mais comum do plano gratuito: as máquinas Ampere (ARM) vivem lotadas. O que fazer, em ordem:

1. Tente de novo em horários diferentes (madrugada costuma funcionar). Não desista na primeira.
2. Troque o **Availability Domain** (AD-1, AD-2, AD-3) na hora de criar.
3. Reduza para **1 OCPU / 6 GB** — pega vaga com mais facilidade e ainda é suficiente.
4. Último recurso: use a máquina AMD **VM.Standard.E2.1.Micro** (1 GB de RAM). Funciona para este app, mas fica apertado; me avise se for esse o caso que eu ajusto a configuração do banco.

---

## 1.3 Entrar na máquina

No seu computador, com o arquivo da chave privada:

```bash
chmod 600 ~/Downloads/ssh-key-2026-09-03.key      # ajuste o nome do arquivo
ssh -i ~/Downloads/ssh-key-2026-09-03.key ubuntu@SEU_IP_PUBLICO
```

O usuário é **`ubuntu`** (não é `root`). Se aparecer "Permission denied", quase sempre é permissão do arquivo da chave (o `chmod 600` acima) ou usuário errado.

> **No Windows**: use o PowerShell (o `ssh` já vem instalado no Windows 10/11) com o mesmo comando, ajustando o caminho do arquivo.

---

## 1.4 Abrir as portas 80 e 443 — as DUAS camadas

Este é o tropeço clássico da Oracle: **existem dois firewalls**, e liberar só um faz o site "não responder" sem nenhuma mensagem de erro.

### Camada 1 — a rede da Oracle (no painel)

1. Na página da instância, clique no link da **Subnet**.
2. Clique na **Security List** (normalmente "Default Security List for ...").
3. **Add Ingress Rules** e adicione **duas** regras:

| Source CIDR | IP Protocol | Destination Port Range |
|---|---|---|
| `0.0.0.0/0` | TCP | `80` |
| `0.0.0.0/0` | TCP | `443` |

Deixe "Stateless" **desmarcado**.

### Camada 2 — o iptables dentro da máquina

A imagem Ubuntu da Oracle vem com o iptables bloqueando quase tudo. Conectado via SSH, rode:

```bash
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT
sudo netfilter-persistent save
```

Pronto — a máquina está criada e acessível.

---

## 1.5 Me mande o diagnóstico

Antes de instalar qualquer coisa, rode este comando na máquina e me mande a saída inteira. Ele só lê informações, não muda nada:

```bash
curl -fsSL https://raw.githubusercontent.com/andre88br/lista/claude/shopping-list-barcode-app-gf9cmb/servidor/diagnostico.sh | bash
```

Se preferir não rodar um script vindo da internet (postura correta, aliás), o arquivo está em `servidor/diagnostico.sh` neste repositório — dá para ler antes, copiar e colar o conteúdo.

Com essa saída eu confirmo memória, Docker, portas e IP, e sigo para o **[passo 2: o subdomínio grátis](02-duckdns.md)**.
