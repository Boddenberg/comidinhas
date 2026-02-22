package br.com.boddenb.comidinhas.data.scraper

/**
 * Interface de descoberta de candidatos de receita.
 *
 * Permite trocar a implementacao (Modo A - busca interna, Modo B - Google)
 * sem impactar o resto do codigo.
 */
interface RecipeLinkDiscovery {
    /**
     * Busca candidatos de receita para o termo dado.
     * @param query termo de busca (ex: "lasanha a bolonhesa")
     * @return lista de candidatos encontrados (pode ser vazia)
     */
    suspend fun discover(query: String): List<RecipeCandidate>
}

/**
 * Interface de extracao de detalhes de uma receita a partir de uma URL.
 */
interface RecipeDetailExtractor {
    /**
     * Extrai os detalhes de uma receita a partir da URL da pagina.
     * @param url URL da pagina do TudoGostoso
     */
    suspend fun extract(url: String): RecipeDetails?
}

