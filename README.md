# Lista & Estoque

App Android para controlar o que tem em casa e o que falta comprar, usando **a leitura do código de barras como única ação**.

## Os três momentos

O app tem três modos. Você escolhe o modo na tela inicial e sai escaneando — cada leitura vale uma unidade.

| Modo | Quando usar | O que cada leitura faz |
|---|---|---|
| **Estou no mercado** | dentro do supermercado | marca como **comprado** e tira da lista de compras |
| **Guardando as compras** | chegando em casa | tira dos comprados e coloca no **estoque** |
| **Acabou** | quando o produto termina | tira do estoque e coloca na **lista de compras** |

O ciclo se fecha sozinho: acabou → lista de compras → comprado no mercado → estoque → acabou de novo.

## O que ele faz

- **Leitura contínua** de códigos EAN-13/EAN-8/UPC/ITF/Code-128 com a câmera, sem precisar tocar na tela entre um produto e outro.
- **Nome automático**: na primeira leitura de um código novo, o app procura o produto no [Open Food Facts](https://world.openfoodfacts.org). Se não achar (ou se você estiver sem internet), é só digitar o nome uma vez — depois aquele código já é reconhecido para sempre.
- **Funciona offline**: banco de dados local, leitor de código de barras embutido no APK. Dentro do mercado, sem sinal, tudo continua funcionando.
- **Desfazer**: leu duas vezes sem querer? Um toque desfaz exatamente a última leitura.
- **Quantidades**: dá para ter 3 unidades do mesmo produto no estoque e 1 na lista ao mesmo tempo.
- **Itens sem código de barras** (frutas, granel): dá para adicionar pelo nome.
- **Guardar tudo de uma vez**: se não quiser reescanear em casa, um botão move tudo que foi comprado para o estoque.
- **Compartilhar a lista** como texto (WhatsApp).
- **Backup** em arquivo JSON, para exportar e importar ao trocar de aparelho.
- **Compartilhar com outra pessoa** (opcional): dois celulares na mesma casa, dividindo estoque, lista e comprados.

## Compartilhar a lista com outra pessoa

Por padrão o app é 100% local. Se você quiser dividir a casa com alguém, existe um servidor que roda no **seu próprio VPS** — nada passa por serviço de terceiros.

1. Crie a máquina: **[docs/01-criar-vps-oracle.md](docs/01-criar-vps-oracle.md)** (plano gratuito da Oracle).
2. Pegue um endereço grátis: **[docs/02-duckdns.md](docs/02-duckdns.md)**.
3. Instale o servidor: **[docs/03-instalar-servidor.md](docs/03-instalar-servidor.md)** — é um script só.
4. No app: **Ajustes → Compartilhar com outra pessoa** → informe o endereço → **Criar casa**. Aparece um código tipo `4KJ2-9WPX`.
5. A outra pessoa instala o app, informa o mesmo endereço e digita o código. Pronto.

A partir daí: ela marca o café como "acabou", aparece na sua lista; você compra no mercado, some da lista dela.

**Continua funcionando sem sinal.** Dentro do mercado, com o celular sem internet, você escaneia normalmente — as leituras sobem sozinhas quando a conexão volta. E se os dois escanearem o mesmo produto offline ao mesmo tempo, os dois chegam ao mesmo número, porque cada leitura viaja como uma variação (`+1 na lista`) e não como um valor absoluto que sobrescreve o do outro.

## Como instalar o APK

Não é preciso ter o Android Studio.

1. Abra a aba **Actions** deste repositório no GitHub.
2. Clique no último build verde da branch (workflow "Android CI").
3. Baixe o artefato **`app-debug-apk`** (é um `.zip` com o `app-debug.apk` dentro).
4. Transfira o APK para o celular, abra e autorize a instalação de apps de fontes desconhecidas quando o Android pedir.
5. Na primeira vez que abrir o scanner, autorize o uso da câmera.

## Como compilar localmente

Precisa do Android SDK (via Android Studio) e JDK 17:

```bash
./gradlew testDebugUnitTest   # testes da regra dos três modos
./gradlew lintDebug           # lint
./gradlew assembleDebug       # gera app/build/outputs/apk/debug/app-debug.apk
```

## Estrutura

```
app/src/main/java/br/com/andre88/lista/
  domain/       regra pura dos três modos (ScanTransitions), sem dependência de Android
  data/db/      Room: produto, item (quantidades) e scan_evento (histórico p/ desfazer)
  data/remote/  consulta ao Open Food Facts
  data/         repositório, backup JSON e preferências
  data/sync/    cliente da API, motor de sincronização e worker de segundo plano
  ui/           Compose: início, scanner, lista de compras, comprados, estoque, casa e ajustes
servidor/       API de sincronização (Node + Postgres + Docker), para rodar no seu VPS
docs/           passo a passo do VPS, do domínio e da instalação
```

O coração do app é `domain/ScanTransitions.kt`: uma função pura que, dado o estado atual do produto e o modo ativo, diz qual é o novo estado. É ela que os testes unitários cobrem.

## Privacidade

Sem compartilhamento, todos os dados ficam apenas no aparelho, e a única chamada de rede é a consulta pública ao Open Food Facts pelo código de barras — que pode ser desligada nos Ajustes.

Com o compartilhamento ligado, os dados vão para **o servidor que você mesmo hospeda**. Ele guarda só o necessário: um identificador anônimo de cada aparelho (o token vai apenas como hash), a casa e seu código, os nomes dos produtos e o histórico de leituras. Sem nome, e-mail, telefone ou localização. Cada consulta é filtrada pela casa do aparelho — um celular nunca enxerga dados de outra casa, e há teste automatizado cobrindo exatamente isso.
