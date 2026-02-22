package br.com.boddenb.comidinhas.ui.screen.home

/**
 * ✅ SALVAMENTO AUTOMÁTICO DE RECEITAS NO AWS
 *
 * IMPLEMENTAÇÃO ATUAL:
 *
 * Todas as receitas retornadas pela OpenAI são AUTOMATICAMENTE salvas
 * no DynamoDB + S3 em background, sem necessidade de interação do usuário.
 *
 * FLUXO:
 * 1. Usuário busca "lasanha"
 * 2. OpenAI retorna 2 receitas
 * 3. ✅ App AUTOMATICAMENTE salva ambas no AWS (background)
 * 4. Usuário vê as receitas normalmente
 * 5. Imagens são copiadas da URL temporária da OpenAI para S3 permanente
 *
 * BENEFÍCIOS:
 * - ✅ Usuário não precisa clicar em "Salvar"
 * - ✅ Todas as receitas ficam disponíveis offline
 * - ✅ Imagens não expiram (S3 vs OpenAI temporário)
 * - ✅ Cache automático para buscas futuras
 * - ✅ Economia de créditos OpenAI (reutiliza receitas)
 *
 * IMPLEMENTAÇÃO NO HomeViewModel:
 *
 * @HiltViewModel
 * class HomeViewModel @Inject constructor(
 *     private val openAiClient: OpenAiClient,
 *     private val saveRecipeUseCase: SaveRecipeUseCase  // ← Injetado
 * ) : ViewModel() {
 *
 *     fun performSearch(mode: SearchMode = SearchMode.PREPARAR) {
 *         when (mode) {
 *             SearchMode.PREPARAR -> {
 *                 viewModelScope.launch {
 *                     val response = openAiClient.searchRecipes(searchQuery)
 *                     recipes = response.results
 *
 *                     // ✅ Salvamento automático em background
 *                     saveRecipesAutomatically(response.results)
 *                 }
 *             }
 *         }
 *     }
 *
 *     private fun saveRecipesAutomatically(recipeItems: List<RecipeItem>) {
 *         viewModelScope.launch {
 *             recipeItems.forEach { recipeItem ->
 *                 val recipe = Recipe(...)
 *                 saveRecipeUseCase(
 *                     recipe = recipe,
 *                     originalImageUrl = recipeItem.imageUrl,
 *                     searchQuery = searchQuery
 *                 )
 *             }
 *         }
 *     }
 * }
 *
 * CARACTERÍSTICAS:
 * - Salvamento em background (não bloqueia UI)
 * - Erros são apenas logados, não exibidos ao usuário
 * - Imagens são copiadas da OpenAI para S3
 * - Query original é salva para buscas futuras
 *
 * MUDANÇAS NA UI:
 * - ❌ NÃO há botão "Salvar" (automático)
 * - ❌ NÃO há feedback visual de salvamento
 * - ✅ Usuário usa o app normalmente
 * - ✅ Tudo funciona como antes, mas com persistência
 *
 * FUTURAS MELHORIAS POSSÍVEIS:
 * 1. Busca híbrida (AWS primeiro, depois OpenAI)
 * 2. Indicador discreto de "salvo no histórico"
 * 3. Cache local com Room para offline total
 * 4. Sincronização em background
 */

