package br.com.boddenb.comidinhas.ui.screen.details

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.boddenb.comidinhas.domain.model.RecipeItem
import br.com.boddenb.comidinhas.data.util.fixEncoding
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.delay


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(
    recipe: RecipeItem?,
    onBack: () -> Unit
) {
    if (recipe == null) {
        Scaffold { paddingValues ->
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("😕", fontSize = 48.sp)
                    Spacer(Modifier.height(16.dp))
                    Text("Receita não encontrada", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF666666))
                }
            }
        }
        return
    }

    // Estado dos ingredientes marcados
    val checkedIngredients = remember { mutableStateMapOf<Int, Boolean>() }
    // Estado do passo ativo
    var activeStep by remember { mutableIntStateOf(-1) }

    val listState = rememberLazyListState()

    // Sticky header: fica visível quando scrollou além da imagem
    val showStickyHeader by remember {
        derivedStateOf { listState.firstVisibleItemIndex >= 1 }
    }

    // Animação de entrada das seções
    var sectionsVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(200)
        sectionsVisible = true
    }

    // Botão voltar: scale ao pressionar
    val backInteraction = remember { MutableInteractionSource() }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(containerColor = Color(0xFFFFFBF7)) { paddingValues ->
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(paddingValues)
            ) {
                // ── Header com imagem ──────────────────────────────────────
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(300.dp)
                    ) {
                        if (!recipe.imageUrl.isNullOrEmpty()) {
                            Image(
                                painter = rememberAsyncImagePainter(model = recipe.imageUrl),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize().background(
                                    Brush.verticalGradient(listOf(Color(0xFFFF6B35), Color(0xFFFF8C42)))
                                ),
                                contentAlignment = Alignment.Center
                            ) { Text("🍽️", fontSize = 80.sp) }
                        }

                        // Gradiente escurecendo de cima e de baixo
                        Box(
                            modifier = Modifier.fillMaxSize().background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.35f),
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.75f)
                                    )
                                )
                            )
                        )

                        // Nome e badges no bottom
                        Column(
                            modifier = Modifier.align(Alignment.BottomStart).padding(24.dp)
                        ) {
                            Text(
                                fixEncoding(recipe.name),
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                lineHeight = 32.sp
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (!recipe.cookingTime.isNullOrEmpty()) {
                                    InfoBadge("⏱", recipe.cookingTime)
                                }
                                if (!recipe.servings.isNullOrEmpty()) {
                                    InfoBadge("🍽", recipe.servings)
                                }
                                InfoBadge("🥘", "${recipe.ingredients.size} itens")
                            }
                        }
                    }
                }

                // ── Conteúdo ──────────────────────────────────────────────
                item {
                    AnimatedVisibility(
                        visible = sectionsVisible,
                        enter = fadeIn(tween(400)) + slideInVertically(
                            initialOffsetY = { it / 4 },
                            animationSpec = tween(400, easing = EaseOutQuart)
                        )
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                        ) {
                            Spacer(Modifier.height(20.dp))

                            // ── Ingredientes ──────────────────────────────
                            SectionHeader(emoji = "🥘", title = "Ingredientes", color = Color(0xFFFF6B35))
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "Toque para marcar o que já tem",
                                fontSize = 12.sp,
                                color = Color(0xFF999999),
                                modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
                            )

                            Card(
                                modifier = Modifier.fillMaxWidth().shadow(3.dp, RoundedCornerShape(20.dp)),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White)
                            ) {
                                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                    recipe.ingredients.forEachIndexed { index, ingredient ->
                                        val isChecked = checkedIngredients[index] == true
                                        IngredientRow(
                                            ingredient = ingredient,
                                            isChecked = isChecked,
                                            onClick = {
                                                checkedIngredients[index] = !isChecked
                                            }
                                        )
                                        if (index < recipe.ingredients.size - 1) {
                                            HorizontalDivider(
                                                modifier = Modifier.padding(start = 56.dp, end = 16.dp),
                                                color = Color(0xFFF0F0F0)
                                            )
                                        }
                                    }
                                }
                            }

                            // Progresso de ingredientes marcados
                            val checkedCount = checkedIngredients.values.count { it }
                            AnimatedVisibility(visible = checkedCount > 0) {
                                Column(modifier = Modifier.padding(top = 10.dp, start = 4.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            "$checkedCount de ${recipe.ingredients.size} separados",
                                            fontSize = 12.sp,
                                            color = Color(0xFFFF6B35),
                                            fontWeight = FontWeight.Medium
                                        )
                                        if (checkedCount == recipe.ingredients.size) {
                                            Text("✅ Tudo pronto!", fontSize = 12.sp, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    val progress by animateFloatAsState(
                                        targetValue = checkedCount.toFloat() / recipe.ingredients.size,
                                        animationSpec = tween(400),
                                        label = "ingredientProgress"
                                    )
                                    LinearProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                        color = Color(0xFFFF6B35),
                                        trackColor = Color(0xFFFFE0D0)
                                    )
                                }
                            }

                            Spacer(Modifier.height(24.dp))

                            // ── Modo de preparo ───────────────────────────
                            SectionHeader(emoji = "👨‍🍳", title = "Modo de Preparo", color = Color(0xFF4CAF50))
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "Toque em um passo para destacar",
                                fontSize = 12.sp,
                                color = Color(0xFF999999),
                                modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
                            )

                            Card(
                                modifier = Modifier.fillMaxWidth().shadow(3.dp, RoundedCornerShape(20.dp)),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White)
                            ) {
                                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                    recipe.instructions.forEachIndexed { index, step ->
                                        val isActive = activeStep == index
                                        StepRow(
                                            index = index,
                                            step = step,
                                            isActive = isActive,
                                            onClick = {
                                                activeStep = if (isActive) -1 else index
                                            }
                                        )
                                        if (index < recipe.instructions.size - 1) {
                                            HorizontalDivider(
                                                modifier = Modifier.padding(start = 56.dp, end = 16.dp),
                                                color = Color(0xFFF0F0F0)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(40.dp))
                        }
                    }
                }
            }
        }

        // ── Botão voltar flutuante (sempre no topo) ────────────────────────
        IconButton(
            onClick = onBack,
            interactionSource = backInteraction,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(12.dp)
                .shadow(6.dp, CircleShape)
                .background(Color.White.copy(alpha = 0.92f), CircleShape)
                .size(40.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Voltar",
                tint = Color(0xFF2C1810),
                modifier = Modifier.size(20.dp)
            )
        }

        // ── Sticky header (aparece ao scrollar) ────────────────────────────
        AnimatedVisibility(
            visible = showStickyHeader,
            enter = fadeIn(tween(200)) + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut(tween(150)) + slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.White, Color.White.copy(alpha = 0.95f))
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 64.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    recipe.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2C1810),
                    maxLines = 1
                )
            }
        }
    }
}

// ─── Componentes auxiliares ────────────────────────────────────────────────

@Composable
private fun InfoBadge(emoji: String, text: String) {
    Surface(shape = RoundedCornerShape(10.dp), color = Color.White.copy(alpha = 0.92f)) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(emoji, fontSize = 13.sp)
            Spacer(Modifier.width(4.dp))
            Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2C1810))
        }
    }
}

@Composable
private fun SectionHeader(emoji: String, title: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(color.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) { Text(emoji, fontSize = 20.sp) }
        Spacer(Modifier.width(12.dp))
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2C1810))
    }
}

@Composable
private fun IngredientRow(ingredient: String, isChecked: Boolean, onClick: () -> Unit) {
    val bgColor by animateColorAsState(
        targetValue = if (isChecked) Color(0xFFF0FFF4) else Color.White,
        animationSpec = tween(250),
        label = "ingredientBg"
    )
    val textAlpha by animateFloatAsState(
        targetValue = if (isChecked) 0.45f else 1f,
        animationSpec = tween(250),
        label = "ingredientAlpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Checkbox animado
        val checkScale by animateFloatAsState(
            targetValue = if (isChecked) 1f else 0f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
            label = "checkScale"
        )
        Box(
            modifier = Modifier
                .size(28.dp)
                .border(
                    1.5.dp,
                    if (isChecked) Color(0xFF4CAF50) else Color(0xFFDDDDDD),
                    CircleShape
                )
                .background(
                    if (isChecked) Color(0xFF4CAF50) else Color.Transparent,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp).scale(checkScale)
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            fixEncoding(ingredient),
            fontSize = 15.sp,
            color = Color(0xFF2C1810),
            lineHeight = 22.sp,
            modifier = Modifier.alpha(textAlpha).weight(1f),
            textDecoration = if (isChecked) TextDecoration.LineThrough else TextDecoration.None
        )
    }
}

@Composable
private fun StepRow(index: Int, step: String, isActive: Boolean, onClick: () -> Unit) {
    val bgColor by animateColorAsState(
        targetValue = if (isActive) Color(0xFFF0FFF4) else Color.White,
        animationSpec = tween(250),
        label = "stepBg"
    )
    val numberColor by animateColorAsState(
        targetValue = if (isActive) Color.White else Color(0xFF4CAF50),
        animationSpec = tween(250),
        label = "numberColor"
    )
    val numberBg by animateColorAsState(
        targetValue = if (isActive) Color(0xFF4CAF50) else Color(0xFF4CAF50).copy(alpha = 0.12f),
        animationSpec = tween(250),
        label = "numberBg"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .background(numberBg, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "${index + 1}",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = numberColor
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            fixEncoding(step),
            fontSize = 15.sp,
            color = if (isActive) Color(0xFF1B5E20) else Color(0xFF2C1810),
            lineHeight = 23.sp,
            fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
    }
}
