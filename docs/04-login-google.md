# 4. Login com Google

O app exige login na primeira abertura. É **uma vez só**: depois a sessão fica guardada no aparelho e o app funciona offline — inclusive dentro do mercado, sem sinal.

O que o login resolve:

- **Trocar de celular** sem perder a casa: quem participa da casa é a pessoa, não o aparelho.
- **Entrar sem código**: no celular novo, basta o login; a casa vem junto.
- **Saber quem escaneou** cada produto, mostrado nas listas.

## 4.1 Criar o projeto no Google Cloud

Em <https://console.cloud.google.com>:

1. **Projeto**: menu do topo → *New Project* → nome `Lista Compras` → *Create*.
2. **Tela de consentimento**: ☰ → *APIs & Services* → *OAuth consent screen* → *Get started*
   - App name: `Lista & Estoque`; e-mail de suporte: o seu
   - Audience: **External**; e-mail de contato: o seu → *Create*
   - Em *Audience*, adicione em **Test users** os e-mails de quem vai usar. **Sem isso o login é recusado.**
3. **Cliente Android**: *Credentials* → *Create credentials* → *OAuth client ID*
   - Application type: **Android**
   - Package name: `br.com.andre88.lista`
   - SHA-1: a impressão digital da chave de assinatura (veja `app/chave/LEIA-ME.md`)
4. **Cliente Web**: *Create credentials* → *OAuth client ID* → **Web application**.
   Guarde o **Client ID** — é ele que vai no app e no servidor.

Os dois clientes têm papéis diferentes e ambos são necessários: o **Android** autoriza este app a pedir o login; o **Web** é a identidade que o servidor confere dentro do token. É o ponto onde mais gente trava.

## 4.2 Configurar o app e o servidor

O mesmo Client ID (o do tipo **Web**) vai nos dois lados.

No repositório, em `gradle.properties`:

```properties
lista.googleClientId=000000000000-xxxxxxxx.apps.googleusercontent.com
```

No servidor, em `servidor/.env`:

```properties
GOOGLE_CLIENT_ID=000000000000-xxxxxxxx.apps.googleusercontent.com
```

Depois, na máquina: `cd ~/lista && git pull && cd servidor && sudo docker compose up -d --build`.

O Client ID **não é segredo** — ele apenas identifica o aplicativo. O que o servidor faz com ele é exigir que o token tenha sido emitido para este app; sem essa checagem, um token válido de qualquer outro aplicativo seria aceito.

## 4.3 Por que a chave de assinatura precisa ser fixa

O Google só aceita o login se a impressão digital de quem assinou o APK for a registrada no cliente Android. O build de debug do Android gera uma chave aleatória por máquina, então essa impressão mudaria a cada build no CI.

Por isso a chave vive encriptada no repositório e o CI a decripta com o segredo `LISTA_KEYSTORE_SENHA`. Detalhes em `app/chave/LEIA-ME.md`.

**Consequência:** o Android recusa atualizar um app quando a chave muda. Ao passar da versão sem login para esta, é preciso **desinstalar e reinstalar**. Os dados locais se perdem nesse momento; o que já está na casa, no servidor, volta ao entrar de novo.

## 4.4 Quando o login falha

| O que aparece | Causa provável |
|---|---|
| "Nenhuma conta Google encontrada" | o aparelho não tem conta Google configurada |
| O seletor de contas nem abre | SHA-1 ou nome do pacote errados no cliente Android, ou APK assinado com outra chave |
| "O Google recusou este login" | o e-mail não está em *Test users*, ou o Client ID do app difere do servidor |
| "O servidor não está configurado" | falta `GOOGLE_CLIENT_ID` no `.env` do servidor |

Para conferir com que chave o APK saiu, o log do CI imprime a impressão digital no passo *"Conferir com que chave o APK foi assinado"*.
