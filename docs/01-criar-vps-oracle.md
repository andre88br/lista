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

## 1.2 Criar a rede primeiro

**Faça esta etapa antes de criar a máquina.** Contas novas não vêm com nenhuma rede virtual, e na hora de criar a instância o campo **Subnet** só lista subnets que já existem — se você for direto para a máquina, esse campo aparece vazio e não há como prosseguir.

1. Menu ☰ → **Networking** → **Virtual Cloud Networks**.
2. Confira no canto superior direito que a **região** é a que você escolheu (São Paulo). Rede criada em outra região não aparece depois.
3. Botão **Start VCN Wizard** → escolha **Create VCN with Internet Connectivity** → **Start VCN Wizard**.
4. **VCN Name**: `rede-lista`. Deixe o resto como vem (`10.0.0.0/16`).
5. **Next** → **Create**.

Em cerca de 30 segundos ele cria a VCN, uma **subnet pública**, uma privada, o internet gateway e as rotas — tudo já configurado para a máquina ser acessível pela internet.

## 1.3 Criar a máquina

No painel (<https://cloud.oracle.com>):

1. Menu ☰ → **Compute** → **Instances** → botão **Create instance**.
2. **Name**: `lista-compras` (qualquer nome serve).
3. **Image and shape** → **Edit**:
   - **Image**: clique em *Change image* e escolha **Canonical Ubuntu 24.04**.
   - **Shape**: clique em *Change shape* → aba **Ampere** → **VM.Standard.A1.Flex**.
     - **OCPUs**: `2` · **Memory**: `12 GB`.
     - (O limite gratuito é 4 OCPUs e 24 GB no total. Usar 2 e 12 deixa folga para você criar outra máquina depois, e sobra muito para este app.)
4. **Primary VNIC / Networking** (use a rede criada no passo anterior):
   - **Virtual cloud network**: `rede-lista`
   - **Subnet**: a que tem **`public`** no nome. Não escolha a `private` — nela a máquina não recebe IP público e você não consegue acessá-la.
   - **Assign a public IPv4 address**: **Yes**

   > **Campo Subnet vazio?** É porque não existe subnet naquela região/compartimento. Volte ao passo 1.2. Se você já criou a VCN, confira duas coisas: a **região** no canto superior direito precisa ser a mesma das duas telas, e o **compartment** no filtro à esquerda precisa ser aquele onde a VCN foi criada (o padrão é o `root`).
5. **Add SSH keys**: escolha **Generate a key pair for me** e clique em **Save private key** — guarde esse arquivo (`ssh-key-....key`), é a única forma de entrar na máquina. Salve também a chave pública.
6. **Create**.

Em 1–2 minutos a instância fica **RUNNING**. Anote o **Public IP address** que aparece na página — vamos usar em tudo daqui para frente.

### Se aparecer "Out of capacity"

```
Out of capacity for shape VM.Standard.A1.Flex in availability domain AD-1.
```

É o erro mais comum do plano gratuito, e **não é problema da sua conta nem da sua configuração**: as máquinas Ampere (ARM) gratuitas vivem lotadas, e a capacidade libera em janelas curtas e imprevisíveis. Quem consegue é quem insiste — por isso o caminho abaixo é automatizar a insistência, não ficar clicando.

#### Caminho 1 (recomendado): deixar um script tentando

A Oracle tem um terminal dentro do próprio painel, o **Cloud Shell**, onde os comandos já vêm autenticados como você. Dá para deixar um script tentando criar a máquina de minuto em minuto.

1. No painel, clique no ícone **`>_`** no canto superior direito (Cloud Shell). A primeira abertura leva ~1 minuto.
2. Cole:

```bash
git clone -b claude/shopping-list-barcode-app-gf9cmb https://github.com/andre88br/lista.git
bash lista/servidor/tentar-a1.sh
```

Ele descobre sozinho a sua rede, a imagem do Ubuntu e os availability domains, gera uma chave SSH e tenta criar a máquina em cada AD, repetindo até conseguir. Pode deixar a aba aberta; quando conseguir, ele imprime o **IP público** e o caminho da chave.

Por padrão ele pede **1 OCPU e 6 GB**, que pega vaga com bem mais facilidade do que 2/12 e é de sobra para este app. Se quiser tentar maior:

```bash
OCPUS=2 MEMORIA_GB=12 bash lista/servidor/tentar-a1.sh
```

> O Cloud Shell desconecta depois de um tempo ocioso; se cair, é só rodar de novo. Cada tentativa é independente, nada quebra por repetir.

#### Caminho 2: a máquina AMD, que está sempre disponível

Se você quer o servidor no ar **hoje**, use a outra máquina do plano gratuito: **VM.Standard.E2.1.Micro** (AMD, 1 GB de RAM). Ela praticamente sempre tem vaga.

Na criação da instância: **Change shape** → aba **AMD** → **VM.Standard.E2.1.Micro**. O resto do guia é igual.

1 GB é pouco, mas o instalador detecta isso sozinho: cria 2 GB de swap e sobe o banco com uma configuração enxuta. Para duas pessoas usando uma lista de compras, dá conta. Se um dia sobrar capacidade ARM, dá para migrar levando um backup (`servidor/backup.sh`).

#### Caminho 3: trocar a conta para "Pay As You Go"

Contas pagas têm prioridade na fila das máquinas ARM, e os recursos **Always Free continuam gratuitos** numa conta paga — você só paga se criar algo além do limite gratuito. É a solução definitiva para o "out of capacity", com o risco de, por descuido, criar um recurso cobrado. Decida com calma; os caminhos 1 e 2 resolvem sem isso.

#### O que *não* adianta

- **Trocar de região**: os recursos Always Free só existem na sua região de origem, e ela não pode ser alterada depois que a conta é criada.
- **Ficar clicando em Create**: funciona, mas é o mesmo que o caminho 1 fazendo você de robô.

---

## 1.4 Entrar na máquina

### Do celular, sem instalar nada (recomendado)

Use o **Cloud Shell** do próprio painel da Oracle (ícone `>_` no canto superior direito) — é um terminal Linux completo, e evita ter que instalar app de SSH no celular. Só falta levar a chave privada para lá:

1. No Cloud Shell, abra o menu (⋮ ou o ícone de engrenagem) → **Upload** → escolha o arquivo `ssh-key-....key` que a Oracle baixou quando você criou a máquina.
2. Depois, no terminal:

```bash
mv ~/ssh-key-*.key ~/.ssh/chave-lista.key
chmod 600 ~/.ssh/chave-lista.key
ssh -i ~/.ssh/chave-lista.key ubuntu@SEU_IP_PUBLICO
```

> Se, ao criar a máquina, você tiver **colado uma chave pública** em vez de gerar uma, use a privada correspondente — o mesmo procedimento de upload.

### Do computador

No seu computador, com o arquivo da chave privada:

```bash
chmod 600 ~/Downloads/ssh-key-2026-09-03.key      # ajuste o nome do arquivo
ssh -i ~/Downloads/ssh-key-2026-09-03.key ubuntu@SEU_IP_PUBLICO
```

O usuário é **`ubuntu`** (não é `root`). Se aparecer "Permission denied", quase sempre é permissão do arquivo da chave (o `chmod 600` acima) ou usuário errado.

> **No Windows**: use o PowerShell (o `ssh` já vem instalado no Windows 10/11) com o mesmo comando, ajustando o caminho do arquivo.

---

## 1.5 Abrir as portas 80 e 443 — as DUAS camadas

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

## 1.6 Me mande o diagnóstico

Antes de instalar qualquer coisa, rode este comando na máquina e me mande a saída inteira. Ele só lê informações, não muda nada:

```bash
curl -fsSL https://raw.githubusercontent.com/andre88br/lista/claude/shopping-list-barcode-app-gf9cmb/servidor/diagnostico.sh | bash
```

Se preferir não rodar um script vindo da internet (postura correta, aliás), o arquivo está em `servidor/diagnostico.sh` neste repositório — dá para ler antes, copiar e colar o conteúdo.

Com essa saída eu confirmo memória, Docker, portas e IP, e sigo para o **[passo 2: o subdomínio grátis](02-duckdns.md)**.
