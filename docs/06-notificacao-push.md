# 6. Notificação push

Sem isso, o outro celular só sabe de uma novidade quando o app está aberto (via SSE) ou a cada 15 minutos, pelo ciclo periódico em segundo plano. Com o Firebase configurado, o servidor acorda o outro aparelho na hora, mesmo com o app fechado.

**É opcional.** Sem configurar nada aqui, o app continua funcionando exatamente como hoje — só não avisa instantaneamente com o app em segundo plano.

## 6.1 Criar o Firebase no mesmo projeto do login

O Firebase se liga ao **mesmo projeto do Google Cloud** que você já usa para o login — não precisa criar um projeto novo.

Em <https://console.firebase.google.com>:

1. *Add project* → escolha o projeto existente (`Lista Compras`, o mesmo do login) → siga o assistente até o fim (pode desligar o Google Analytics, não é usado).
2. ☰ → *Project settings* → aba **General** → em *Your apps*, clique no ícone Android → *Register app*:
   - Android package name: `br.com.andre88.lista`
   - Não precisa do SHA-1 aqui (é só para outros produtos do Firebase, não para notificação).
   - Pule o download do `google-services.json` e os passos de SDK — este app não usa esse arquivo (veja o porquê em 6.3).
3. Ainda em *Project settings* → **General**, na seção do app Android, anote os 4 valores:
   - **API Key** (`apiKey`)
   - **App ID** (`appId`, no formato `1:123456789:android:abcdef`)
   - **Project ID** (`projectId`)
   - **Project number** (é o `gcmSenderId`)

## 6.2 Configurar o app

Em `gradle.properties`:

```properties
lista.firebaseApiKey=AIzaSy...
lista.firebaseAppId=1:106047364042:android:xxxxxxxx
lista.firebaseProjectId=lista-compras-xxxxx
lista.firebaseSenderId=106047364042
```

Nenhum desses valores é segredo — eles só identificam o app perante o Firebase, do mesmo jeito que o Client ID do login. Sem eles (deixando em branco), a notificação push fica desligada e o resto do app funciona normalmente.

## 6.3 Por que sem `google-services.json`

O caminho comum usa o plugin `google-services` e um arquivo `google-services.json` baixado do console. Aqui optamos por **inicializar o Firebase na mão**, com `FirebaseOptions` e os 4 valores acima vindos de `gradle.properties` — o mesmo padrão já usado para o Client ID do login (`docs/04-login-google.md`). Evita mais um arquivo para gerenciar e mais um plugin no build, por uma configuração que já é só string.

## 6.4 Configurar o servidor

O envio de verdade (quem manda o push) precisa de uma **conta de serviço** do Firebase — isso, sim, é uma credencial e não pode ser commitada no repositório.

1. *Project settings* → aba **Service accounts** → *Generate new private key* → confirma o download. Guarde este arquivo com cuidado: com ele dá para mandar notificação para qualquer aparelho registrado.
2. Na VPS, comprima o JSON numa linha só e cole em `servidor/.env`:

   ```bash
   cd ~/lista/servidor
   nano .env
   # adicione a linha (troque pelo conteudo do arquivo que voce baixou):
   # FIREBASE_SERVICE_ACCOUNT_JSON=<cole aqui o resultado do jq -c abaixo>
   ```

   Para gerar o JSON numa linha só:

   ```bash
   sudo apt-get install -y jq   # só na primeira vez
   jq -c . caminho/para/a-chave-baixada.json
   ```

   Copie a saída inteira para depois do `=` em `FIREBASE_SERVICE_ACCOUNT_JSON=` no `.env`.

3. Suba de novo: `cd ~/lista && git pull && cd servidor && sudo docker compose up -d --build`.

Sem essa variável, o servidor sobe normalmente e a notificação simplesmente não é enviada — nenhuma rota quebra.

## 6.5 O que esperar

- A pessoa recebe uma notificação do tipo "3 novidades na sua casa" quando o outro aparelho sincroniza algo novo. Tocar nela abre o app.
- Quem escaneou **não** recebe notificação da própria leitura.
- Android 13 ou mais novo pede a permissão de notificação na tela "Compartilhar com outra pessoa" — se negar, o app continua sincronizando (pelo SSE e pelo ciclo periódico), só não mostra o aviso.
- Um token de aparelho que o Google já descartou (desinstalou o app, por exemplo) é limpo sozinho na próxima tentativa de envio.
