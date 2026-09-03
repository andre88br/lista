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
  ui/           Compose: início, scanner, lista de compras, comprados, estoque e ajustes
```

O coração do app é `domain/ScanTransitions.kt`: uma função pura que, dado o estado atual do produto e o modo ativo, diz qual é o novo estado. É ela que os testes unitários cobrem.

## Privacidade

Todos os dados ficam apenas no aparelho. A única chamada de rede é a consulta pública ao Open Food Facts pelo código de barras — e ela pode ser desligada nos Ajustes.
