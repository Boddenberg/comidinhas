package br.com.boddenb.comidinhas.ui.components

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.boddenb.comidinhas.domain.model.RecipeItem
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeCard(
    item: RecipeItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val TAG = "RecipeCard"

    LaunchedEffect(item.imageUrl) {
        Log.d(TAG, "Iniciando carregamento da imagem: ${item.imageUrl}")
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        onClick = onClick
    ) {
        Box(modifier = Modifier.height(220.dp)) {
            val painter = rememberAsyncImagePainter(
                model = item.imageUrl,
                onState = { state ->
                    when (state) {
                        is AsyncImagePainter.State.Loading -> {
                            Log.d(TAG, "Carregando imagem: ${item.name}")
                        }
                        is AsyncImagePainter.State.Success -> {
                            Log.d(TAG, "Imagem carregada com sucesso: ${item.name}")
                        }
                        is AsyncImagePainter.State.Error -> {
                            Log.e(TAG, "Erro ao carregar imagem: ${item.name}")
                            Log.e(TAG, "   URL: ${item.imageUrl}")
                        }
                        is AsyncImagePainter.State.Empty -> {
                            Log.w(TAG, "Estado vazio: ${item.name}")
                        }
                    }
                }
            )
            val imageState = painter.state

            // Fundo sempre presente
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )

            // Imagem (fica por cima do fundo)
            Image(
                painter = painter,
                contentDescription = item.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Loading ou erro (só aparece quando necessário)
            when (imageState) {
                is AsyncImagePainter.State.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(40.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                is AsyncImagePainter.State.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Restaurant,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                    }
                }
                else -> {
                    // Imagem carregada com sucesso, não mostra nada sobre ela
                }
            }

            // Gradiente sobre a imagem (sempre visível)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0x99000000),
                                Color(0xDD000000)
                            ),
                            startY = 200f
                        )
                    )
            )

            // Título da receita (sempre visível)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.BottomStart
            ) {
                Column {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 22.sp
                        ),
                        maxLines = 2
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Restaurant,
                            contentDescription = null,
                            tint = Color(0xFFFFB74D),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Ver receita completa",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFFB74D),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

