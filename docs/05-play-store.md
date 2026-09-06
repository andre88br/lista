# 5. Publicar na faixa de teste interno da Play

O objetivo aqui não é lançar o app para o público, e sim **parar de instalar APK na mão**: a faixa de *teste interno* entrega o app pela própria loja, com atualização automática, para até 100 pessoas que você escolhe.

O que ela evita, em comparação com publicar de verdade: análise de conteúdo, os 12 testadores por 14 dias que a Play exige de contas pessoais recentes, e a responsabilidade por dados pessoais de desconhecidos no seu servidor.

## 5.1 A armadilha que quebra o login: a Play reassina o app

Esta é a parte que mais gente erra, então ela vem primeiro.

Você envia o pacote assinado com a **sua** chave (a chave de envio). O Google então assina o app de novo com a **chave dele** antes de entregar aos aparelhos. Resultado: a impressão digital que chega ao login do Google **não é** a que registramos (`17:D4:4F:76:…`).

Se você não fizer nada, o login funciona no APK que você instala na mão e **falha para todo mundo que baixar da loja**.

A correção, depois de enviar o primeiro pacote:

1. Play Console → seu app → **Teste e lançamento → Configuração → Assinatura do app**
2. Copie o **SHA-1 do certificado de assinatura do app** (não o do certificado de envio)
3. Google Cloud → **Credentials** → crie **outro** cliente OAuth do tipo **Android**:
   - Package name: `br.com.andre88.lista`
   - SHA-1: o que você copiou
4. Mantenha o cliente antigo: ele continua valendo para os APKs que você instala direto

Com os dois registrados, o login funciona nas duas formas de instalar.

## 5.2 Gerar o pacote

No GitHub: aba **Actions** → workflow **"Pacote para a Play Store"** → **Run workflow** → informe o nome da versão (ex.: `1.0`) → **Run**.

Ao terminar, baixe o artefato **`aab-para-a-play`**. É um `.aab`, o formato que a Play exige.

O `versionCode` vem do número da execução do workflow, então cresce sozinho a cada pacote — a Play recusa um envio com número igual ou menor que o anterior.

## 5.3 Criar o app no Play Console

1. **Criar app**: nome `Lista & Estoque`, idioma padrão português (Brasil), tipo **App**, **Gratuito**.
2. **Teste e lançamento → Teste → Teste interno → Criar novo lançamento**
3. Envie o `.aab`. Aceite o Play App Signing quando for oferecido.
4. **Testadores**: crie uma lista de e-mails com os seus dois (ou mais). Precisam ser contas Google.
5. Copie o **link de participação** e mande para quem vai usar. Cada pessoa aceita o convite e instala pela loja.

O console vai listar pendências em **Política → Conteúdo do app**. Para teste interno, o conjunto exigido é menor que o de produção, mas ele diz exatamente o que falta. Duas você já tem prontas:

- **Política de privacidade**: `https://SEU-DOMINIO/privacidade` (servida pelo próprio servidor).
- **Exclusão de conta**: existe no app, em *Ajustes → Compartilhar com outra pessoa → Excluir minha conta*.

## 5.4 O que muda para quem instalar pela loja

Nada no uso. O app aponta para o mesmo servidor embutido no pacote, e o login e a sincronização funcionam igual — desde que o SHA-1 da seção 5.1 esteja registrado.

## 5.5 Se um dia quiser publicar para o público

Aí o conjunto muda, e vale saber o tamanho antes de decidir:

- teste fechado com **12 testadores por 14 dias** (conta pessoal criada depois de 13/11/2023);
- formulário de **segurança de dados** declarando o que é coletado;
- **classificação de conteúdo**;
- ficha de loja completa: ícone de 512×512, imagem de destaque 1024×500, capturas de tela, descrições;
- e o ponto que não é burocrático: você passa a guardar dados pessoais de desconhecidos num VPS de 1 GB que você mantém, com as obrigações de LGPD que vêm junto — incluindo responder a pedidos de exclusão.
