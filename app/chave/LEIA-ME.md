# Chave de assinatura

`chave-lista.jks.enc` é a chave que assina o APK, guardada aqui **encriptada**.
A senha não está no repositório: ela vive como segredo do GitHub, em
`LISTA_KEYSTORE_SENHA`, e é usada tanto para decriptar o arquivo quanto como
senha do próprio keystore.

Por que ela precisa ser fixa: o login com Google só funciona se a impressão
digital da chave estiver registrada no console do Google. Com a chave aleatória
que o build de debug gera por padrão, essa impressão mudaria a cada build.

**Impressão digital (SHA-1)** — é a que está registrada no Google Cloud:

```
17:D4:4F:76:CE:F9:39:13:A5:25:3F:13:54:3C:49:0E:6E:5F:58:42
```

Para decriptar localmente, se algum dia precisar:

```bash
openssl enc -d -aes-256-cbc -pbkdf2 -iter 100000 \
  -in app/chave/chave-lista.jks.enc -out chave-lista.jks -pass pass:SENHA
```

Se esta chave for perdida, o app não pode mais ser atualizado por cima: seria
preciso gerar outra, registrar a nova impressão no Google e desinstalar e
reinstalar o app nos aparelhos.
