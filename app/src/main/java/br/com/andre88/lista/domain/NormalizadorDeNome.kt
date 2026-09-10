package br.com.andre88.lista.domain

import java.text.Normalizer

/**
 * Reduz o nome de um produto vindo de uma base publica (que costuma juntar marca,
 * tipo e peso num campo so) ao essencial para uma lista de compras: a categoria
 * (ex.: "Café") e, so quando existe, a caracteristica que de fato muda o que vai na
 * sacola (ex.: "moído" x "solúvel" x "em cápsulas"). Marca, peso e palavras de
 * marketing somem. A mesma tabela de categorias tambem diz em que secao do mercado
 * o produto costuma estar (ver [corredorDe]), usado para agrupar a lista de compras.
 *
 * Cobre as categorias mais comuns de mercado. Um produto fora da lista so tem a
 * marca e o peso removidos, sem forcar numa categoria errada — e a lista e so um
 * ponto de partida, facil de estender com novas entradas em [CATEGORIAS].
 */
object NormalizadorDeNome {

    fun normalizar(nomeBruto: String, marca: String? = null): String {
        val bruto = nomeBruto.trim()
        if (bruto.isEmpty()) return bruto

        val categoria = categoriaDe(bruto, marca)
            ?: return capitalizar(removerRuido(bruto, marca).ifBlank { bruto })

        val buscaCompleta = semAcento(bruto)
        val caracteristicas = categoria.caracteristicas
            .filter { c -> c.padroes.any { contemPalavra(buscaCompleta, it) } }
            .take(2)

        return if (caracteristicas.isEmpty()) {
            categoria.nomeCanonico
        } else {
            categoria.nomeCanonico + " " + caracteristicas.joinToString(" ") { it.rotulo }
        }
    }

    /** Secao do mercado do produto (Laticínios, Padaria...), para agrupar a lista de compras. */
    fun corredorDe(nome: String, marca: String? = null): String = categoriaDe(nome, marca)?.corredor ?: "Outros"

    private fun categoriaDe(nomeBruto: String, marca: String?): Categoria? {
        // A categoria e buscada no nome inteiro, nao so no que sobra depois de tirar a
        // marca. E quando nem isso acha (o tipo pode vir embutido so na marca, tipo
        // "Nescafé"), tenta a marca por conter a palavra, sem exigir que seja uma
        // palavra inteira ali dentro.
        val buscaCompleta = semAcento(nomeBruto)
        val buscaMarca = marca?.let(::semAcento)
        return CATEGORIAS.firstOrNull { cat -> cat.sinonimos.any { contemPalavra(buscaCompleta, it) } }
            ?: buscaMarca?.let { texto -> CATEGORIAS.firstOrNull { cat -> cat.sinonimos.any { texto.contains(it) } } }
    }

    /** Usado so quando nenhuma categoria e reconhecida: tira marca, peso e pontuacao solta. */
    private fun removerRuido(nome: String, marca: String?): String {
        var texto = nome
        if (!marca.isNullOrBlank()) {
            texto = Regex("\\b${Regex.escape(marca.trim())}\\b", RegexOption.IGNORE_CASE).replace(texto, " ")
        }
        texto = PADRAO_TAMANHO.replace(texto, " ")
        texto = texto.replace(Regex("[,;|]"), " ")
        return texto.replace(Regex("\\s+"), " ").trim()
    }

    private fun contemPalavra(textoSemAcento: String, padrao: String): Boolean =
        Regex("\\b${Regex.escape(padrao)}\\b").containsMatchIn(textoSemAcento)

    private fun semAcento(texto: String): String {
        val decomposto = Normalizer.normalize(texto, Normalizer.Form.NFD)
        return decomposto.replace(Regex("\\p{Mn}+"), "").lowercase()
    }

    private fun capitalizar(texto: String): String =
        texto.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

    private val PADRAO_TAMANHO = Regex(
        "\\b\\d+([.,]\\d+)?\\s?(kg|g|mg|l|ml|un|und|unid(ades)?|pct|cx)\\b",
        RegexOption.IGNORE_CASE,
    )

    private data class Caracteristica(val rotulo: String, val padroes: List<String>) {
        constructor(rotulo: String, padrao: String) : this(rotulo, listOf(padrao))
    }

    private data class Categoria(
        val nomeCanonico: String,
        val corredor: String,
        val sinonimos: List<String>,
        val caracteristicas: List<Caracteristica> = emptyList(),
    )

    private val CATEGORIAS = listOf(
        Categoria(
            "Café", "Mercearia", listOf("cafe"),
            listOf(
                Caracteristica("em cápsulas", listOf("capsulas", "capsula")),
                Caracteristica("solúvel", "soluvel"),
                Caracteristica("em grãos", listOf("graos", "em grao")),
                Caracteristica("moído", "moido"),
                Caracteristica("descafeinado", "descafeinado"),
            ),
        ),
        Categoria(
            "Leite", "Laticínios", listOf("leite", "leites"),
            listOf(
                Caracteristica("em pó", "em po"),
                Caracteristica("condensado", "condensado"),
                Caracteristica("de coco", "de coco"),
                Caracteristica("semidesnatado", "semidesnatado"),
                Caracteristica("desnatado", "desnatado"),
                Caracteristica("integral", "integral"),
                Caracteristica("sem lactose", listOf("sem lactose", "zero lactose")),
            ),
        ),
        Categoria(
            "Açúcar", "Mercearia", listOf("acucar", "acucares"),
            listOf(
                Caracteristica("refinado", "refinado"),
                Caracteristica("cristal", "cristal"),
                Caracteristica("demerara", "demerara"),
                Caracteristica("mascavo", "mascavo"),
            ),
        ),
        Categoria(
            "Arroz", "Mercearia", listOf("arroz"),
            listOf(
                Caracteristica("integral", "integral"),
                Caracteristica("parboilizado", "parboilizado"),
                Caracteristica("branco", "branco"),
            ),
        ),
        Categoria(
            "Feijão", "Mercearia", listOf("feijao", "feijoes"),
            listOf(
                Caracteristica("carioca", "carioca"),
                Caracteristica("preto", "preto"),
                Caracteristica("fradinho", "fradinho"),
                Caracteristica("branco", "branco"),
            ),
        ),
        Categoria(
            "Macarrão", "Mercearia", listOf("macarrao", "massa", "massas"),
            listOf(
                Caracteristica("integral", "integral"),
                Caracteristica("espaguete", "espaguete"),
                Caracteristica("parafuso", "parafuso"),
                Caracteristica("penne", "penne"),
            ),
        ),
        Categoria(
            "Azeite", "Mercearia", listOf("azeite"),
            listOf(Caracteristica("extra virgem", "extra virgem")),
        ),
        Categoria(
            "Óleo", "Mercearia", listOf("oleo", "oleos"),
            listOf(
                Caracteristica("de soja", "soja"),
                Caracteristica("de girassol", "girassol"),
                Caracteristica("de canola", "canola"),
                Caracteristica("de milho", "milho"),
            ),
        ),
        Categoria(
            "Biscoito", "Mercearia", listOf("biscoito", "biscoitos", "bolacha", "bolachas"),
            listOf(
                Caracteristica("recheado", "recheado"),
                Caracteristica("água e sal", "agua e sal"),
                Caracteristica("cream cracker", "cream cracker"),
                Caracteristica("integral", "integral"),
                Caracteristica("doce", "doce"),
                Caracteristica("salgado", "salgado"),
            ),
        ),
        Categoria(
            "Iogurte", "Laticínios", listOf("iogurte", "iogurtes"),
            listOf(
                Caracteristica("natural", "natural"),
                Caracteristica("grego", "grego"),
                Caracteristica("desnatado", "desnatado"),
                Caracteristica("light", "light"),
                Caracteristica("zero açúcar", "zero acucar"),
                Caracteristica("com frutas", "com frutas"),
            ),
        ),
        Categoria(
            "Queijo", "Laticínios", listOf("queijo", "queijos"),
            listOf(
                Caracteristica("mussarela", "mussarela"),
                Caracteristica("prato", "prato"),
                Caracteristica("minas", "minas"),
                Caracteristica("parmesão", "parmesao"),
                Caracteristica("ralado", "ralado"),
            ),
        ),
        Categoria(
            "Manteiga", "Laticínios", listOf("manteiga"),
            listOf(
                Caracteristica("com sal", "com sal"),
                Caracteristica("sem sal", "sem sal"),
            ),
        ),
        Categoria(
            "Margarina", "Laticínios", listOf("margarina"),
            listOf(
                Caracteristica("com sal", "com sal"),
                Caracteristica("sem sal", "sem sal"),
                Caracteristica("light", "light"),
            ),
        ),
        Categoria(
            "Farinha", "Mercearia", listOf("farinha"),
            listOf(
                Caracteristica("de trigo", "de trigo"),
                Caracteristica("de mandioca", "de mandioca"),
                Caracteristica("de rosca", "de rosca"),
                Caracteristica("integral", "integral"),
            ),
        ),
        Categoria(
            "Pão", "Padaria", listOf("pao", "paes"),
            listOf(
                Caracteristica("de forma", "de forma"),
                Caracteristica("francês", "frances"),
                Caracteristica("integral", "integral"),
                Caracteristica("sem glúten", "sem gluten"),
            ),
        ),
        Categoria(
            "Água", "Bebidas", listOf("agua", "aguas"),
            listOf(
                Caracteristica("com gás", "com gas"),
                Caracteristica("sem gás", "sem gas"),
            ),
        ),
        Categoria(
            "Refrigerante", "Bebidas", listOf("refrigerante", "refrigerantes"),
            listOf(
                Caracteristica("zero", "zero"),
                Caracteristica("diet", "diet"),
                Caracteristica("light", "light"),
            ),
        ),
        Categoria(
            "Papel higiênico", "Higiene e limpeza", listOf("papel higienico"),
            listOf(
                Caracteristica("folha dupla", "folha dupla"),
                Caracteristica("folha simples", "folha simples"),
                Caracteristica("neutro", "neutro"),
                Caracteristica("perfumado", "perfumado"),
            ),
        ),
    )
}
