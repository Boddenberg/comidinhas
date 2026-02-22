package br.com.boddenb.comidinhas.data.repository.aws

import android.graphics.Bitmap
import br.com.boddenb.comidinhas.data.model.recipe.RecipeEntity
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecipeAwsRepository @Inject constructor(
    private val supabase: SupabaseClient
) {

    private val TABLE = "comidinhas-recipe"
    private val BUCKET = "comidinhas-recipe-images"

    companion object {
        private const val TAG = "RecipeAwsRepository"
        private fun log(level: String, message: String, throwable: Throwable? = null) {
            try {
                val logClass = Class.forName("android.util.Log")
                when (level) {
                    "d" -> logClass.getMethod("d", String::class.java, String::class.java).invoke(null, TAG, message)
                    "e" -> {
                        if (throwable != null) {
                            logClass.getMethod("e", String::class.java, String::class.java, Throwable::class.java).invoke(null, TAG, message, throwable)
                        } else {
                            logClass.getMethod("e", String::class.java, String::class.java).invoke(null, TAG, message)
                        }
                    }
                    "w" -> logClass.getMethod("w", String::class.java, String::class.java).invoke(null, TAG, message)
                }
            } catch (_: Exception) {
                val prefix = when (level) { "d" -> "DEBUG"; "e" -> "ERROR"; "w" -> "WARN"; else -> "INFO" }
                println("[$prefix] $TAG: $message")
                throwable?.printStackTrace()
            }
        }
    }

    private fun normalizeKey(text: String): String = text.lowercase()
        .replace("á", "a").replace("à", "a").replace("ã", "a").replace("â", "a")
        .replace("é", "e").replace("ê", "e")
        .replace("í", "i")
        .replace("ó", "o").replace("ô", "o").replace("õ", "o")
        .replace("ú", "u").replace("ü", "u")
        .replace("ç", "c")
        .trim()

    suspend fun saveRecipe(recipe: RecipeEntity): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val normalizedQuery = normalizeKey(recipe.searchQuery ?: "")

            if (recipeExistsInDynamoDB(recipe.id)) {
                log("d", "📦 Receita JÁ EXISTE no Supabase (não sobrescrevendo): ${recipe.name} (id: ${recipe.id})")
                return@withContext Result.success(Unit)
            }

            supabase.from(TABLE).insert(mapOf(
                "id" to recipe.id,
                "name" to recipe.name,
                "ingredients" to recipe.ingredients,
                "instructions" to recipe.instructions,
                "imageUrl" to recipe.imageUrl,
                "cookingTime" to recipe.cookingTime,
                "servings" to recipe.servings,
                "searchQuery" to normalizedQuery,
                "createdAt" to recipe.createdAt,
                "source" to recipe.source
            ))

            log("d", "💾 NOVA RECEITA salva no Supabase: ${recipe.name} (id: ${recipe.id}, query: $normalizedQuery)")
            Result.success(Unit)
        } catch (e: Exception) {
            log("e", "❌ Erro ao salvar receita no Supabase", e)
            Result.failure(e)
        }
    }

    suspend fun getAllRecipes(): Result<List<RecipeEntity>> = withContext(Dispatchers.IO) {
        try {
            val rows = supabase.from(TABLE).select(columns = Columns.ALL)
            val recipes = rows.decodeList<RecipeEntity>()
            log("d", "✅ ${recipes.size} receitas carregadas do Supabase")
            Result.success(recipes)
        } catch (e: Exception) {
            log("e", "❌ Erro ao buscar receitas do Supabase", e)
            Result.failure(e)
        }
    }

    suspend fun getRecipesByQuery(query: String): Result<List<RecipeEntity>> = withContext(Dispatchers.IO) {
        try {
            val normalizedQuery = normalizeKey(query)
            log("d", "🔍 Buscando receitas no Supabase para: '$query' (normalizada: '$normalizedQuery')")
            val rows = supabase.from(TABLE).select {
                filter { eq("searchQuery", normalizedQuery) }
            }
            val recipes = rows.decodeList<RecipeEntity>()
            if (recipes.isEmpty()) {
                log("d", "📭 Nenhuma receita encontrada no Supabase para: '$query'")
            } else {
                log("d", "📦 ${recipes.size} RECEITA(S) ENCONTRADA(S) NO SUPABASE para: '$query'")
                recipes.forEach { r -> log("d", "   ✓ ${r.name} (id: ${r.id})") }
            }
            Result.success(recipes)
        } catch (e: Exception) {
            log("e", "❌ Erro ao buscar receitas por query no Supabase", e)
            Result.failure(e)
        }
    }

    suspend fun deleteRecipe(recipeId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            supabase.from(TABLE).delete { filter { eq("id", recipeId) } }
            log("d", "✅ Receita deletada: id=$recipeId")
            Result.success(Unit)
        } catch (e: Exception) {
            log("e", "❌ Erro ao deletar receita no Supabase", e)
            Result.failure(e)
        }
    }

    suspend fun uploadRecipeImage(bitmap: Bitmap, recipeId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
            val imageBytes = outputStream.toByteArray()
            val fileName = "$recipeId.jpg"
            val bucket = supabase.storage.from(BUCKET)
            bucket.upload(fileName, imageBytes) {
                contentType = ContentType.Image.JPEG
                upsert = false
            }
            val publicUrl = bucket.publicUrl(fileName)
            log("d", "✅ Imagem enviada para Storage: $publicUrl")
            Result.success(publicUrl)
        } catch (e: Exception) {
            log("e", "❌ Erro ao enviar imagem para Storage", e)
            Result.failure(e)
        }
    }

    suspend fun uploadRecipeImageFromUrl(imageUrl: String, recipeId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val fileName = "$recipeId.jpg"
            val bucket = supabase.storage.from(BUCKET)
            if (imageExistsInS3(recipeId)) {
                val existingUrl = bucket.publicUrl(fileName)
                log("d", "🖼️ IMAGEM JÁ EXISTE no Storage (reutilizando): $existingUrl")
                return@withContext Result.success(existingUrl)
            }
            val connection = java.net.URL(imageUrl).openConnection().apply {
                connectTimeout = 10000
                readTimeout = 10000
            }
            val imageBytes = connection.getInputStream().use { it.readBytes() }
            bucket.upload(fileName, imageBytes) {
                contentType = ContentType.Image.JPEG
                upsert = false
            }
            val publicUrl = bucket.publicUrl(fileName)
            log("d", "💾 NOVA IMAGEM salva no Storage: $publicUrl")
            Result.success(publicUrl)
        } catch (e: Exception) {
            log("e", "❌ Erro ao copiar imagem para Storage", e)
            Result.failure(e)
        }
    }

    suspend fun imageExistsInS3(recipeId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val fileName = "$recipeId.jpg"
            val bucket = supabase.storage.from(BUCKET)
            val files = bucket.list()
            files.any { it.name == fileName }
        } catch (e: Exception) {
            log("w", "⚠️ Erro ao verificar existência de imagem no Storage: ${e.message}")
            false
        }
    }

    suspend fun recipeExistsInDynamoDB(recipeId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val rows = supabase.from(TABLE).select {
                filter { eq("id", recipeId) }
            }
            val found = rows.decodeList<RecipeEntity>()
            found.isNotEmpty()
        } catch (e: Exception) {
            log("w", "⚠️ Erro ao verificar existência de receita no Supabase: ${e.message}")
            false
        }
    }

    suspend fun deleteRecipeImage(recipeId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val fileName = "$recipeId.jpg"
            val bucket = supabase.storage.from(BUCKET)
            bucket.delete(fileName)
            log("d", "✅ Imagem deletada do Storage: $fileName")
            Result.success(Unit)
        } catch (e: Exception) {
            log("e", "❌ Erro ao deletar imagem do Storage", e)
            Result.failure(e)
        }
    }
}
