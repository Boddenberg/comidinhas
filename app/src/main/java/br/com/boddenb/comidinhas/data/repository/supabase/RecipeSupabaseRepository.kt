package br.com.boddenb.comidinhas.data.repository.supabase

import android.graphics.Bitmap
import br.com.boddenb.comidinhas.BuildConfig
import br.com.boddenb.comidinhas.data.logger.AppLogger
import br.com.boddenb.comidinhas.data.model.recipe.RecipeEntity
import br.com.boddenb.comidinhas.data.repository.RecipeRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.storage.storage
import br.com.boddenb.comidinhas.data.util.TextNormalizer
import io.ktor.http.ContentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable

@Singleton
class RecipeSupabaseRepository @Inject constructor(
    private val supabase: SupabaseClient
) : RecipeRepository {
    companion object {
        private const val TABLE  = "comidinhas-recipe"
        private const val BUCKET = "comidinhas-recipe-images"
    }

    private fun normalizeKey(text: String): String = TextNormalizer.normalize(text)

    private fun buildPublicUrl(path: String): String {
        val base = BuildConfig.SUPABASE_URL.trimEnd('/')
        return "$base/storage/v1/object/public/$BUCKET/$path"
    }

    override suspend fun saveRecipe(recipe: RecipeEntity): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val normalizedQuery = normalizeKey(recipe.searchQuery ?: "")
            if (recipeExists(recipe.id)) {
                AppLogger.d(AppLogger.SUPABASE, "📦 Receita já existe (ignorando): \"${recipe.name}\" (id: ${recipe.id})")
                return@withContext Result.success(Unit)
            }
            val toInsert = recipe.copy(searchQuery = normalizedQuery)
            supabase.from(TABLE).insert(toInsert)
            AppLogger.supabaseSaveRecipe(recipe.name, recipe.id)
            Result.success(Unit)
        } catch (e: Exception) {
            AppLogger.supabaseError("saveRecipe(${recipe.name})", e.message ?: "Exceção")
            Result.failure(e)
        }
    }

    override suspend fun getAllRecipes(): Result<List<RecipeEntity>> = withContext(Dispatchers.IO) {
        try {
            val recipes = supabase.from(TABLE).select(columns = Columns.ALL).decodeList<RecipeEntity>()
            AppLogger.d(AppLogger.SUPABASE, "📥 ${recipes.size} receita(s) carregadas (getAllRecipes)")
            Result.success(recipes)
        } catch (e: Exception) {
            AppLogger.supabaseError("getAllRecipes", e.message ?: "Exceção")
            Result.failure(e)
        }
    }

    override suspend fun getRecipesByQuery(query: String): Result<List<RecipeEntity>> = withContext(Dispatchers.IO) {
        try {
            val normalizedQuery = normalizeKey(query)
            AppLogger.supabaseSearchStart(normalizedQuery)
            val recipes = supabase.from(TABLE).select {
                filter { eq("searchQuery", normalizedQuery) }
            }.decodeList<RecipeEntity>()
            if (recipes.isEmpty()) {
                AppLogger.supabaseSearchEmpty(normalizedQuery)
            } else {
                AppLogger.supabaseSearchFound(normalizedQuery, recipes.size)
                recipes.forEach { r -> AppLogger.d(AppLogger.SUPABASE, "   • \"${r.name}\" (id: ${r.id}, imagem: ${if (r.imageUrl.isNullOrEmpty()) "ausente" else "✓"})") }
            }
            Result.success(recipes)
        } catch (e: Exception) {
            AppLogger.supabaseError("getRecipesByQuery($query)", e.message ?: "Exceção")
            Result.failure(e)
        }
    }

    override suspend fun deleteRecipe(recipeId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            supabase.from(TABLE).delete { filter { eq("id", recipeId) } }
            AppLogger.d(AppLogger.SUPABASE, "🗑️ Receita deletada: id=$recipeId")
            Result.success(Unit)
        } catch (e: Exception) {
            AppLogger.supabaseError("deleteRecipe($recipeId)", e.message ?: "Exceção")
            Result.failure(e)
        }
    }

    override suspend fun uploadRecipeImage(bitmap: Bitmap, recipeId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
            val imageBytes = outputStream.toByteArray()
            val path = "$recipeId/receita.jpg"
            AppLogger.supabaseUploadImageStart(path)
            val bucket = supabase.storage.from(BUCKET)
            bucket.upload(path, imageBytes) { contentType = ContentType.Image.JPEG; upsert = false }
            val publicUrl = buildPublicUrl(path)
            AppLogger.supabaseUploadImageOk(path, publicUrl)
            Result.success(publicUrl)
        } catch (e: Exception) {
            AppLogger.supabaseUploadImageFail("$recipeId/receita.jpg", e.message ?: "Exceção")
            Result.failure(e)
        }
    }

    override suspend fun uploadRecipeImageFromUrl(imageUrl: String, recipeId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val path = "$recipeId/receita.jpg"
            val bucket = supabase.storage.from(BUCKET)
            if (imageExists(recipeId)) {
                val existingUrl = buildPublicUrl(path)
                AppLogger.d(AppLogger.SUPABASE, "🖼️ Imagem já existe no Storage (reutilizando): $existingUrl")
                return@withContext Result.success(existingUrl)
            }
            AppLogger.d(AppLogger.SUPABASE, "⬇️ Baixando imagem de: $imageUrl")
            val connection = java.net.URL(imageUrl).openConnection().apply {
                connectTimeout = 10000
                readTimeout = 10000
            }
            val imageBytes = connection.getInputStream().use { it.readBytes() }
            AppLogger.supabaseUploadImageStart(path)
            bucket.upload(path, imageBytes) { contentType = ContentType.Image.JPEG; upsert = false }
            val publicUrl = buildPublicUrl(path)
            AppLogger.supabaseUploadImageOk(path, publicUrl)
            Result.success(publicUrl)
        } catch (e: Exception) {
            AppLogger.supabaseUploadImageFail("$recipeId/receita.jpg", e.message ?: "Exceção")
            Result.failure(e)
        }
    }

    override suspend fun imageExists(recipeId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = buildPublicUrl("$recipeId/receita.jpg")
            val conn = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
                requestMethod = "HEAD"; connectTimeout = 5000; readTimeout = 5000
            }
            conn.responseCode in 200..299
        } catch (e: Exception) {
            AppLogger.d(AppLogger.SUPABASE, "⚠️ imageExists($recipeId): ${e.message}")
            false
        }
    }

    override suspend fun recipeExists(recipeId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val list = supabase.from(TABLE).select { filter { eq("id", recipeId) } }.decodeList<RecipeEntity>()
            list.isNotEmpty()
        } catch (e: Exception) {
            AppLogger.d(AppLogger.SUPABASE, "⚠️ recipeExists($recipeId): ${e.message}")
            false
        }
    }

    override suspend fun deleteRecipeImage(recipeId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val path = "$recipeId/receita.jpg"
            supabase.storage.from(BUCKET).delete(path)
            AppLogger.d(AppLogger.SUPABASE, "🗑️ Imagem deletada do Storage: $path")
            Result.success(Unit)
        } catch (e: Exception) {
            AppLogger.supabaseError("deleteRecipeImage($recipeId)", e.message ?: "Exceção")
            Result.failure(e)
        }
    }
}
