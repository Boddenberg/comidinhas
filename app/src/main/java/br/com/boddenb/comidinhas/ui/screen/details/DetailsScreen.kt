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
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.RoomService
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.boddenb.comidinhas.data.util.fixEncoding
import br.com.boddenb.comidinhas.domain.model.RecipeItem
import br.com.boddenb.comidinhas.ui.screen.theme.*
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(
    recipe: RecipeItem?,
    onBack: () -> Unit
) {
    if (recipe == null) {
        Scaffold(containerColor = Surface0) { paddingValues ->
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("😕", fontSize = 52.sp)
                    Spacer(Modifier.height(Spacing.md))
                    Text(
                        "Receita não encontrada",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = InkLight
                        )
                    )
                }
            }
        }
        return
    }

    val checkedIngredients = remember { mutableStateMapOf<Int, Boolean>() }
    var activeStep by remember { mutableIntStateOf(-1) }
    val listState = rememberLazyListState()

    val showStickyHeader by remember {
        derivedStateOf { listState.firstVisibleItemIndex >= 1 }
    }

    var sectionsVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(200); sectionsVisible = true }

    Box(modifier = Modifier.fillMaxSize()) {
        // ── Container principal off-white ─────────────────────────────
        Scaffold(
            containerColor = Surface0
        ) { paddingValues ->
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                verticalArrangement = Arrangement.spacedBy(0.dp),
                // garante que o último item não fique atrás do FAB
                contentPadding = PaddingValues(bottom = Spacing.xxl)
            ) {

                // ── Bloco 1: Hero ──────────────────────────────────────
                item { RecipeHeroHeader(recipe = recipe) }

                // ── Bloco 2: Ingredientes ──────────────────────────────
                item {
                    AnimatedVisibility(
                        visible = sectionsVisible,
                        enter = fadeIn(tween(350)) + slideInVertically(
                            initialOffsetY = { it / 6 },
                            animationSpec = tween(400, easing = EaseOutQuart)
                        )
                    ) {
                        ContentBlock {
                            IngredientsCard(
                                ingredients = recipe.ingredients,
                                checkedMap = checkedIngredients,
                                onToggle = { index ->
                                    checkedIngredients[index] = !(checkedIngredients[index] ?: false)
                                }
                            )
                        }
                    }
                }

                // ── Bloco 3: Modo de preparo ───────────────────────────
                item {
                    AnimatedVisibility(
                        visible = sectionsVisible,
                        enter = fadeIn(tween(400)) + slideInVertically(
                            initialOffsetY = { it / 6 },
                            animationSpec = tween(450, easing = EaseOutQuart)
                        )
                    ) {
                        ContentBlock(topPadding = 0.dp) {
                            StepsTimelineCard(
                                steps = recipe.instructions,
                                selectedIndex = activeStep,
                                onSelect = { index ->
                                    activeStep = if (activeStep == index) -1 else index
                                }
                            )
                        }
                    }
                }

                // ── Rodapé de respiro (coberto pelo contentPadding do LazyColumn) ──
            }
        }

        // ── Botão voltar flutuante ─────────────────────────────────────
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(Spacing.md - 2.dp)
                .shadow(4.dp, CircleShape, spotColor = Color.Black.copy(alpha = 0.10f))
                .background(Color.White.copy(alpha = 0.95f), CircleShape)
                .size(42.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Voltar",
                tint = Ink,
                modifier = Modifier.size(20.dp)
            )
        }

        // ── Sticky header ──────────────────────────────────────────────
        AnimatedVisibility(
            visible = showStickyHeader,
            enter = fadeIn(tween(200)) + slideInVertically(initialOffsetY = { -it }),
            exit  = fadeOut(tween(150)) + slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Surface0.copy(alpha = 0.97f),
                shadowElevation = 3.dp,
                tonalElevation = 0.dp
            ) {
                Box(
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(horizontal = 68.dp, vertical = Spacing.md - 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        fixEncoding(recipe.name),
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Ink
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// ─── Layout helper ────────────────────────────────────────────────────────────

@Composable
private fun ContentBlock(
    topPadding: androidx.compose.ui.unit.Dp = Spacing.sectionGap,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = Spacing.screenHorizontal,
                end = Spacing.screenHorizontal,
                top = topPadding,
                bottom = 0.dp
            ),
        content = content
    )
}

// ─── Hero Header ──────────────────────────────────────────────────────────────

@Composable
fun RecipeHeroHeader(recipe: RecipeItem) {
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    val heroHeight = with(density) {
        (windowInfo.containerSize.height * 0.35f).toDp().coerceIn(220.dp, 300.dp)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(heroHeight)
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
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Brand, BrandLight))),
                contentAlignment = Alignment.Center
            ) { Text("🍽️", fontSize = 80.sp) }
        }

        // Gradiente de legibilidade — só escurece no rodapé
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.00f to Color.Transparent,
                            0.48f to Color.Transparent,
                            0.75f to Color.Black.copy(alpha = 0.55f),
                            1.00f to Color.Black.copy(alpha = 0.88f)
                        )
                    )
                )
        )

        // Título + chips sobre o gradiente
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(
                    start = Spacing.screenHorizontal,
                    end = Spacing.screenHorizontal,
                    bottom = Spacing.lg - 4.dp
                )
        ) {
            Text(
                text = fixEncoding(recipe.name),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            )
            Spacer(Modifier.height(Spacing.sm + Spacing.xs))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                if (!recipe.cookingTime.isNullOrEmpty()) {
                    HeroStatChip(Icons.Outlined.AccessTime, recipe.cookingTime)
                }
                if (!recipe.servings.isNullOrEmpty()) {
                    HeroStatChip(Icons.Outlined.RoomService, recipe.servings)
                }
                HeroStatChip(
                    Icons.AutoMirrored.Outlined.FormatListBulleted,
                    "${recipe.ingredients.size} itens"
                )
            }
        }
    }
}

@Composable
private fun HeroStatChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = 0.90f),
        shadowElevation = 2.dp,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = Spacing.sm + Spacing.xs,
                vertical = 7.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(icon, contentDescription = null, tint = Ink, modifier = Modifier.size(14.dp))
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        }
    }
}

// ─── Componentes auxiliares ───────────────────────────────────────────────────

@Composable
fun IngredientsCard(
    ingredients: List<String>,
    checkedMap: androidx.compose.runtime.snapshots.SnapshotStateMap<Int, Boolean>,
    onToggle: (Int) -> Unit
) {
    val checkedCount = checkedMap.values.count { it }

    val foodEmojis = remember {
        listOf(
            "🥘","🍝","🍜","🍛","🍲","🥗","🍕","🍔","🌮","🌯",
            "🥙","🧆","🥚","🍳","🥞","🧇","🥓","🥩","🍗","🍖",
            "🌭","🥪","🫔","🧀","🥦","🥕","🧅","🧄","🍅","🫑",
            "🍣","🍱","🍤","🦐","🦑","🥟","🫕","🍠","🥜","🫘"
        )
    }
    var emojiIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1500)
            emojiIndex = (emojiIndex + 1) % foodEmojis.size
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {

            // ── Cabeçalho ──────────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Brand.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = emojiIndex,
                        transitionSpec = {
                            fadeIn(tween(400)) togetherWith fadeOut(tween(400))
                        },
                        label = "emojiCycle"
                    ) { idx ->
                        Text(foodEmojis[idx], fontSize = 20.sp)
                    }
                }
                Spacer(Modifier.width(Spacing.sm + Spacing.xs))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Ingredientes",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Ink
                        )
                    )
                    Text(
                        "Toque para marcar o que já tem",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = InkLight
                        )
                    )
                }
                // Contador de marcados
                AnimatedVisibility(visible = checkedCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(50.dp),
                        color = Brand.copy(alpha = 0.12f)
                    ) {
                        Text(
                            "$checkedCount/${ingredients.size}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Brand,
                            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 3.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(Spacing.md))

            // ── Lista de ingredientes ───────────────────────────────────
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Surface0,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                Column {
                    ingredients.forEachIndexed { index, ingredient ->
                        IngredientRow(
                            ingredient = ingredient,
                            isChecked = checkedMap[index] == true,
                            onClick = { onToggle(index) }
                        )
                        if (index < ingredients.size - 1) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 56.dp, end = Spacing.md),
                                thickness = 0.5.dp,
                                color = Surface2.copy(alpha = 0.45f)
                            )
                        }
                    }
                }
            }

            // ── Barra de progresso ──────────────────────────────────────
            AnimatedVisibility(visible = checkedCount > 0) {
                Column(modifier = Modifier.padding(top = Spacing.md)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "$checkedCount de ${ingredients.size} separados",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = Brand,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        if (checkedCount == ingredients.size) {
                            Text(
                                "✅ Tudo pronto!",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = Green,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                    Spacer(Modifier.height(Spacing.xs + 2.dp))
                    val progress by animateFloatAsState(
                        targetValue = checkedCount.toFloat() / ingredients.size,
                        animationSpec = tween(400),
                        label = "ingredientProgress"
                    )
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(50.dp)),
                        color = Brand,
                        trackColor = BrandSurface
                    )
                }
            }
        }
    }
}

@Composable
fun IngredientRow(ingredient: String, isChecked: Boolean, onClick: () -> Unit) {
    val bgColor by animateColorAsState(
        targetValue = if (isChecked) GreenSurface.copy(alpha = 0.6f) else Color.Transparent,
        animationSpec = tween(250), label = "ingredientBg"
    )
    val textAlpha by animateFloatAsState(
        targetValue = if (isChecked) 0.38f else 1f,
        animationSpec = tween(250), label = "ingredientAlpha"
    )
    val checkScale by animateFloatAsState(
        targetValue = if (isChecked) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "checkScale"
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
            .padding(horizontal = Spacing.md, vertical = Spacing.md - 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Checkbox circular custom
        Box(
            modifier = Modifier
                .size(26.dp)
                .border(
                    width = 1.5.dp,
                    color = if (isChecked) Green else Surface2,
                    shape = CircleShape
                )
                .background(
                    color = if (isChecked) Green else Color.Transparent,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .size(14.dp)
                    .scale(checkScale)
            )
        }

        Spacer(Modifier.width(Spacing.md - 2.dp))

        Text(
            text = fixEncoding(ingredient),
            style = MaterialTheme.typography.bodyMedium.copy(
                color = InkMedium,
                textDecoration = if (isChecked) TextDecoration.LineThrough else TextDecoration.None
            ),
            lineHeight = 22.sp,
            modifier = Modifier
                .alpha(textAlpha)
                .weight(1f)
        )
    }
}

@Composable
fun StepsTimelineCard(
    steps: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {

            // ── Cabeçalho ──────────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Green.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) { Text("👨‍🍳", fontSize = 20.sp) }
                Spacer(Modifier.width(Spacing.sm + Spacing.xs))
                Column {
                    Text(
                        "Modo de Preparo",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Ink
                        )
                    )
                    Text(
                        "Toque em um passo para destacar",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = InkLight
                        )
                    )
                }
            }

            Spacer(Modifier.height(Spacing.md))

            // ── Timeline de passos ──────────────────────────────────────
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Surface0,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                Column(modifier = Modifier.padding(vertical = Spacing.sm)) {
                    steps.forEachIndexed { index, step ->
                        StepTimelineItem(
                            index = index,
                            text = step,
                            isLast = index == steps.lastIndex,
                            isSelected = selectedIndex == index,
                            onClick = { onSelect(index) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StepTimelineItem(
    index: Int,
    text: String,
    isLast: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) GreenSurface.copy(alpha = 0.7f) else Color.Transparent,
        animationSpec = tween(220), label = "stepBg"
    )
    val bubbleBg by animateColorAsState(
        targetValue = if (isSelected) Green else Green.copy(alpha = 0.15f),
        animationSpec = tween(220), label = "bubbleBg"
    )
    val bubbleTextColor by animateColorAsState(
        targetValue = if (isSelected) Color.White else Green,
        animationSpec = tween(220), label = "bubbleText"
    )

    // Largura fixa da coluna esquerda (bolha + linha)
    val timelineColumnWidth = 44.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(
                start = Spacing.md,
                end = Spacing.md,
                top = Spacing.sm,
                bottom = if (isLast) Spacing.sm else 0.dp
            )
    ) {
        // ── Coluna esquerda: bolha + linha vertical ─────────────────
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(timelineColumnWidth)
        ) {
            // Bolha numerada
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(bubbleBg, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = bubbleTextColor
                    )
                )
            }

            // Linha vertical conectora (exceto no último passo)
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .defaultMinSize(minHeight = 40.dp)
                        .fillMaxHeight()
                        .background(
                            brush = Brush.verticalGradient(
                                listOf(
                                    Green.copy(alpha = 0.35f),
                                    Green.copy(alpha = 0.10f)
                                )
                            ),
                            shape = RoundedCornerShape(50.dp)
                        )
                )
            }
        }

        Spacer(Modifier.width(Spacing.sm + Spacing.xs))

        // ── Coluna direita: texto do passo ──────────────────────────
        Text(
            text = fixEncoding(text),
            style = MaterialTheme.typography.bodyMedium.copy(
                color = if (isSelected) Color(0xFF1B5E20) else InkMedium,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
            ),
            lineHeight = 24.sp,
            modifier = Modifier
                .weight(1f)
                .padding(
                    top = Spacing.xs,
                    bottom = if (isLast) 0.dp else Spacing.lg
                )
        )
    }
}
