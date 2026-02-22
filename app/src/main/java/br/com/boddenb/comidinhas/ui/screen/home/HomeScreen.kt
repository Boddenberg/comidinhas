package br.com.boddenb.comidinhas.ui.screen.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.boddenb.comidinhas.R
import br.com.boddenb.comidinhas.domain.model.RecipeItem
import br.com.boddenb.comidinhas.domain.model.SearchMode
import br.com.boddenb.comidinhas.ui.screen.theme.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.delay

@Composable
fun shimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = 0f, targetValue = 1200f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerX"
    )
    return Brush.linearGradient(
        colors = listOf(Color(0xFFF0EDE8), Color(0xFFFAF8F5), Color(0xFFF0EDE8)),
        start = androidx.compose.ui.geometry.Offset(x - 600f, 0f),
        end   = androidx.compose.ui.geometry.Offset(x, 0f)
    )
}

@Composable
fun RecipeCardSkeleton() {
    val brush = shimmerBrush()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(brush)
    )
}

private val suggestions = listOf(
    "Lasanha", "Bolo de cenoura",
    "Risoto", "Tacos", "Feijoada", "Ramen", "Pizza"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onRecipeClick: (RecipeItem) -> Unit,
    onOpenEatOut: (String) -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    var searchFocused by remember { mutableStateOf(false) }
    var showDeliveryComingSoon by remember { mutableStateOf(false) }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    fun requestSearch() { viewModel.requestSearch(); keyboardController?.hide() }
    fun performSearch(mode: SearchMode) {
        if (mode == SearchMode.DELIVERY) { showDeliveryComingSoon = true; return }
        if (mode == SearchMode.OUT) { viewModel.performSearch(mode); onOpenEatOut(uiState.searchQuery); return }
        viewModel.performSearch(mode); keyboardController?.hide()
    }

    val hasContent = uiState.recipes.isNotEmpty() || uiState.isLoading || uiState.errorMessage != null

    // A lista de resultados é um estado interno da rota "home", não uma tela separada.
    // Sem este handler, o botão voltar do sistema fecha o app em vez de limpar os resultados.
    BackHandler(enabled = hasContent) {
        viewModel.clearSearch()
    }

    val HomeBg = Color(0xFFF8F8F8)

    Scaffold(containerColor = HomeBg) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {

            AnimatedVisibility(
                visible = !hasContent,
                enter = fadeIn(tween(300)),
                exit  = fadeOut(tween(180))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(HomeBg)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {

                        Spacer(Modifier.height(24.dp))

                        var logoVisible by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) { delay(60); logoVisible = true }
                        AnimatedVisibility(
                            visible = logoVisible,
                            enter = fadeIn(tween(500)) + scaleIn(
                                initialScale = 0.80f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            )
                        ) {
                            Image(
                                painter = painterResource(R.drawable.logo_comidinhas_bell),
                                contentDescription = "Comidinhas",
                                modifier = Modifier
                                    .fillMaxWidth(0.95f)
                                    .height(260.dp),
                                contentScale = ContentScale.Fit
                            )
                        }

                        Spacer(Modifier.height(4.dp))

                        var headlineVisible by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) { delay(200); headlineVisible = true }
                        AnimatedVisibility(
                            visible = headlineVisible,
                            enter = fadeIn(tween(600)) + slideInVertically(
                                initialOffsetY = { it / 4 },
                                animationSpec = tween(500, easing = EaseOutQuart)
                            )
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            ) {
                                Text(
                                    "sua fome pede quais",
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.Light,
                                    color = Ink.copy(alpha = 0.45f),
                                    letterSpacing = (-0.3).sp,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    "comidinhas?",
                                    fontSize = 46.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Brand,
                                    letterSpacing = (-2).sp,
                                    lineHeight = 50.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        Spacer(Modifier.height(32.dp))

                        var barVisible by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) { delay(320); barVisible = true }
                        AnimatedVisibility(
                            visible = barVisible,
                            enter = fadeIn(tween(450)) + slideInVertically(
                                initialOffsetY = { it / 4 },
                                animationSpec = tween(400, easing = EaseOutQuart)
                            )
                        ) {
                            val borderColor by animateColorAsState(
                                targetValue = if (searchFocused) Brand else Color(0xFFDDDDDD),
                                animationSpec = tween(200), label = "border"
                            )
                            val shadowElev by animateDpAsState(
                                targetValue = if (searchFocused) 8.dp else 2.dp,
                                animationSpec = tween(200), label = "shadow"
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp)
                                    .shadow(shadowElev, RoundedCornerShape(18.dp), spotColor = Brand.copy(alpha = 0.12f))
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(Color.White)
                                    .border(1.5.dp, borderColor, RoundedCornerShape(18.dp))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(58.dp)
                                        .padding(horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val iconColor by animateColorAsState(
                                        targetValue = if (searchFocused) Brand else InkLight,
                                        animationSpec = tween(200), label = "iconColor"
                                    )
                                    Icon(
                                        Icons.Default.Search, null,
                                        tint = iconColor,
                                        modifier = Modifier.padding(start = 14.dp).size(22.dp)
                                    )
                                    OutlinedTextField(
                                        value = uiState.searchQuery,
                                        onValueChange = { viewModel.onSearchQueryChange(it) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .onFocusChanged { searchFocused = it.isFocused },
                                        placeholder = {
                                            Text("lasanha, sushi, bolo...", color = InkLight, fontSize = 15.sp)
                                        },
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor   = Color.Transparent,
                                            unfocusedContainerColor = Color.Transparent,
                                            focusedBorderColor      = Color.Transparent,
                                            unfocusedBorderColor    = Color.Transparent,
                                            cursorColor             = Brand,
                                            focusedTextColor        = Ink,
                                            unfocusedTextColor      = Ink
                                        ),
                                        textStyle = androidx.compose.ui.text.TextStyle(
                                            fontSize = 15.sp, fontWeight = FontWeight.Medium
                                        ),
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                        keyboardActions = KeyboardActions(onSearch = { requestSearch() })
                                    )
                                    AnimatedVisibility(
                                        visible = uiState.searchQuery.isNotEmpty(),
                                        enter = fadeIn() + scaleIn(initialScale = 0.7f),
                                        exit  = fadeOut() + scaleOut(targetScale = 0.7f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .padding(end = 6.dp)
                                                .height(44.dp)
                                                .clip(RoundedCornerShape(13.dp))
                                                .background(Brand)
                                                .clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null
                                                ) { requestSearch() }
                                                .padding(horizontal = 20.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("Buscar", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.weight(1f))

                        var tagsVisible by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) { delay(500); tagsVisible = true }
                        AnimatedVisibility(
                            visible = tagsVisible,
                            enter = fadeIn(tween(500))
                        ) {
                            Column(modifier = Modifier.padding(bottom = 32.dp)) {
                                Row(
                                    modifier = Modifier.padding(start = 22.dp, end = 20.dp, bottom = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(Modifier.size(5.dp).background(Brand, CircleShape))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "MAIS BUSCADOS",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = InkLight,
                                        letterSpacing = 2.sp
                                    )
                                }
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 20.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(suggestions.size) { i ->
                                        HomeSuggestionChip(
                                            label = suggestions[i],
                                            delayMs = i * 45L,
                                            onClick = {
                                                viewModel.onSearchQueryChange(suggestions[i])
                                                requestSearch()
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = hasContent,
                enter = fadeIn(tween(300)),
                exit  = fadeOut(tween(200))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFFFAF8F5))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White)
                            .shadow(2.dp, RoundedCornerShape(0.dp), spotColor = Color(0x14000000))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFF5F0EB))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { viewModel.clearSearch() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    null,
                                    tint = Ink,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            val searchBorderColor by animateColorAsState(
                                if (searchFocused) Brand else Color.Transparent,
                                label = "b2"
                            )
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFF5F0EB))
                                    .border(1.dp, searchBorderColor, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Search,
                                    null,
                                    tint = InkLight,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                BasicTextField(
                                    value = uiState.searchQuery,
                                    onValueChange = { viewModel.onSearchQueryChange(it) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(vertical = 12.dp)
                                        .onFocusChanged { searchFocused = it.isFocused },
                                    singleLine = true,
                                    textStyle = androidx.compose.ui.text.TextStyle(
                                        fontSize = 13.sp,
                                        color = Ink,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    cursorBrush = SolidColor(Brand),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(onSearch = { requestSearch() }),
                                    decorationBox = { inner ->
                                        if (uiState.searchQuery.isEmpty()) {
                                            Text("Nova busca...", color = InkLight, fontSize = 13.sp)
                                        }
                                        inner()
                                    }
                                )
                            }
                            AnimatedVisibility(
                                visible = uiState.searchQuery.isNotEmpty(),
                                enter = fadeIn() + scaleIn(initialScale = 0.8f),
                                exit  = fadeOut() + scaleOut(targetScale = 0.8f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(start = 8.dp)
                                        .height(38.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Brand)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) { requestSearch() }
                                        .padding(horizontal = 14.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Ir", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                                }
                            }
                        }
                    }

                    AnimatedVisibility(visible = hasContent, enter = fadeIn(tween(200))) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    "Resultados para",
                                    fontSize = 11.sp,
                                    color = InkLight,
                                    letterSpacing = 0.3.sp
                                )
                                Text(
                                    (uiState.displayQuery.ifBlank { uiState.searchQuery })
                                        .replaceFirstChar { it.uppercase() },
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Ink,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            uiState.selectedMode?.let { mode ->
                                val (emoji, label, color) = when (mode) {
                                    SearchMode.PREPARAR -> Triple("🍳", "Preparar", Brand)
                                    SearchMode.DELIVERY -> Triple("🛵", "Delivery", Green)
                                    SearchMode.OUT      -> Triple("🗺️", "Sair", Blue)
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50.dp))
                                        .background(color.copy(alpha = 0.10f))
                                        .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(50.dp))
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        "$emoji  $label",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = color
                                    )
                                }
                            }
                        }
                    }

                    when {
                        uiState.isLoading -> {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Spacer(Modifier.height(4.dp))
                                repeat(3) { RecipeCardSkeleton() }
                            }
                        }
                        uiState.errorMessage != null -> {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("😕", fontSize = 52.sp)
                                    Spacer(Modifier.height(16.dp))
                                    Text(
                                        uiState.errorMessage ?: "",
                                        fontSize = 15.sp,
                                        color = InkMedium,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 22.sp
                                    )
                                    Spacer(Modifier.height(24.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50.dp))
                                            .background(Brand)
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null
                                            ) { viewModel.dismissError() }
                                            .padding(horizontal = 28.dp, vertical = 14.dp)
                                    ) {
                                        Text("Tentar novamente", fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                            }
                        }
                        uiState.recipes.isNotEmpty() -> {
                            LazyColumn(
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                itemsIndexed(uiState.recipes) { index, recipe ->
                                    var visible by remember { mutableStateOf(false) }
                                    LaunchedEffect(Unit) { delay(index * 60L); visible = true }
                                    AnimatedVisibility(
                                        visible = visible,
                                        enter = fadeIn(tween(250)) + slideInVertically(
                                            initialOffsetY = { it / 4 },
                                            animationSpec = tween(250, easing = EaseOutQuart)
                                        )
                                    ) {
                                        RecipeCard(recipe = recipe, onClick = { onRecipeClick(recipe) })
                                    }
                                }
                                item { Spacer(Modifier.height(24.dp)) }
                            }
                        }
                    }
                }
            }

            if (showDeliveryComingSoon) {
                AlertDialog(
                    onDismissRequest = { showDeliveryComingSoon = false },
                    containerColor = Color.White,
                    shape = RoundedCornerShape(20.dp),
                    icon = { Text("🛵", fontSize = 40.sp) },
                    title = {
                        Text(
                            "Em breve!",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            color = Ink,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    text = {
                        Text(
                            "A função de delivery está sendo preparada com muito carinho e estará disponível em breve.",
                            fontSize = 14.sp,
                            color = InkMedium,
                            textAlign = TextAlign.Center,
                            lineHeight = 21.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50.dp))
                                    .background(Brand)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { showDeliveryComingSoon = false }
                                    .padding(horizontal = 32.dp, vertical = 12.dp)
                            ) {
                                Text("Entendido!", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                            }
                        }
                    }
                )
            }

            AnimatedVisibility(
                visible = uiState.showModeSelection,
                enter = fadeIn(tween(180)),
                exit  = fadeOut(tween(150))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable { viewModel.hideModeSelection() },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    AnimatedVisibility(
                        visible = uiState.showModeSelection,
                        enter = slideInVertically(
                            initialOffsetY = { it },
                            animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)
                        ) + fadeIn(tween(180)),
                        exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(200)) + fadeOut(tween(180))
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = false) {},
                            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(0.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(24.dp)
                                    .navigationBarsPadding(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(36.dp)
                                        .height(4.dp)
                                        .background(Color(0xFFE0D8D0), RoundedCornerShape(2.dp))
                                )
                                Spacer(Modifier.height(20.dp))
                                Text(
                                    "Como quer aproveitar?",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 18.sp,
                                    color = Ink
                                )
                                Text(
                                    "\"${uiState.searchQuery}\"",
                                    fontSize = 13.sp,
                                    color = InkLight,
                                    fontStyle = FontStyle.Italic,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
                                )
                                AnimatedModeButton("🍳", "Preparar em casa",  "Receita passo a passo",      Brand) { performSearch(SearchMode.PREPARAR) }
                                Spacer(Modifier.height(10.dp))
                                AnimatedModeButton("🛵", "Pedir delivery",    "Entrega onde você está",     Green) { performSearch(SearchMode.DELIVERY) }
                                Spacer(Modifier.height(10.dp))
                                AnimatedModeButton("🗺️", "Comer fora",       "Restaurantes perto de você", Blue)  { performSearch(SearchMode.OUT) }
                                Spacer(Modifier.height(8.dp))
                                TextButton(
                                    onClick = { viewModel.hideModeSelection() },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Cancelar", color = InkLight, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeSuggestionChip(label: String, delayMs: Long, onClick: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(delayMs); visible = true }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(280)) + scaleIn(
            initialScale = 0.88f,
            animationSpec = spring(Spring.DampingRatioMediumBouncy)
        )
    ) {
        val source = remember { MutableInteractionSource() }
        val pressed by source.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (pressed) 0.94f else 1f,
            animationSpec = spring(Spring.DampingRatioMediumBouncy),
            label = "chipScale"
        )
        Box(
            modifier = Modifier
                .scale(scale)
                .clip(RoundedCornerShape(50.dp))
                .background(Color(0xFFF5F0EB))
                .clickable(source, null) { onClick() }
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = InkMedium)
        }
    }
}

@Composable
fun AnimatedModeButton(emoji: String, title: String, subtitle: String, color: Color, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy),
        label = "modeScale"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.07f))
            .border(1.dp, color.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
            .clickable(source, null) { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) { Text(emoji, fontSize = 20.sp) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title,    fontWeight = FontWeight.Bold,   fontSize = 14.sp, color = Ink)
            Text(subtitle, fontWeight = FontWeight.Normal, fontSize = 12.sp, color = InkLight)
        }
        Text("›", fontSize = 20.sp, color = color.copy(alpha = 0.6f), fontWeight = FontWeight.Light)
    }
}

@Composable
fun RecipeCard(recipe: RecipeItem, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy),
        label = "cardScale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .shadow(
                elevation = if (pressed) 1.dp else 3.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = Color.Black.copy(alpha = 0.08f)
            )
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .clickable(source, null) { onClick() }
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().height(190.dp)) {
                if (!recipe.imageUrl.isNullOrEmpty()) {
                    Image(
                        painter = rememberAsyncImagePainter(recipe.imageUrl),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFFF5F0EB)),
                        contentAlignment = Alignment.Center
                    ) { Text("🍽️", fontSize = 52.sp) }
                }
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.55f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.50f)
                        )
                    )
                )
                Row(
                    modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (!recipe.cookingTime.isNullOrEmpty()) BadgePill("⏱ ${recipe.cookingTime}")
                    if (!recipe.servings.isNullOrEmpty())    BadgePill("🍽 ${recipe.servings}")
                }
            }

            Column(modifier = Modifier.padding(14.dp, 12.dp, 14.dp, 14.dp)) {
                Text(
                    recipe.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Ink,
                    maxLines = 2,
                    lineHeight = 20.sp,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TagChip("🥘 ${recipe.ingredients.size} itens",   BrandSurface, BrandDark)
                    TagChip("👨‍🍳 ${recipe.instructions.size} passos", GreenSurface,  Green)
                }
                if (recipe.ingredients.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFFAF8F5))
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(4.dp).background(Brand, CircleShape))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${recipe.ingredients.first()}${if (recipe.ingredients.size > 1) "  +${recipe.ingredients.size - 1} mais" else ""}",
                            fontSize = 12.sp,
                            color = InkLight,
                            maxLines = 1,
                            fontStyle = FontStyle.Italic
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BadgePill(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .background(Color.Black.copy(alpha = 0.40f))
            .padding(horizontal = 9.dp, vertical = 5.dp)
    ) {
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
}

@Composable
private fun TagChip(text: String, bg: Color, textColor: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = textColor)
    }
}
