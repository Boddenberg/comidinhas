package br.com.boddenb.comidinhas.ui.screen.eatout

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.boddenb.comidinhas.domain.model.Restaurant
import br.com.boddenb.comidinhas.ui.screen.theme.*
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComerForaRoute(
    onBack: () -> Unit,
    onOpenDetails: (String) -> Unit,
    initialFoodQuery: String = "",
    vm: ComerForaViewModel = hiltViewModel()
) {
    if (initialFoodQuery.isNotBlank()) {
        vm.onEvent(ComerForaEvent.SearchFood(initialFoodQuery))
    }

    val state by vm.state.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Comer fora",
                        fontFamily = NunitoFamily,
                        fontWeight = FontWeight.ExtraBold,
                        color = Ink
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .padding(4.dp)
                            .shadow(4.dp, CircleShape)
                            .background(MaterialTheme.colorScheme.surface, CircleShape)
                            .size(36.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = Ink, modifier = Modifier.size(18.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                is ComerForaUiState.Loading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = Brand, strokeWidth = 3.dp, modifier = Modifier.size(44.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("Buscando restaurantes...", color = InkLight, fontFamily = NunitoFamily, fontSize = 15.sp)
                    }
                }
                is ComerForaUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                            Text("😕", fontSize = 48.sp)
                            Spacer(Modifier.height(12.dp))
                            Text(s.message, color = InkMedium, fontFamily = NunitoFamily, fontSize = 15.sp)
                        }
                    }
                }
                is ComerForaUiState.Empty -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                            Text("🍽️", fontSize = 48.sp)
                            Spacer(Modifier.height(12.dp))
                            Text("Nenhum restaurante encontrado por aqui", color = InkMedium, fontFamily = NunitoFamily, fontSize = 15.sp)
                        }
                    }
                }
                is ComerForaUiState.Success -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(s.items) { index, r ->
                            var visible by remember { mutableStateOf(false) }
                            LaunchedEffect(Unit) { delay(index * 60L); visible = true }
                            AnimatedVisibility(
                                visible = visible,
                                enter = fadeIn(tween(280)) + slideInVertically(initialOffsetY = { it / 3 }, animationSpec = tween(280))
                            ) {
                                RestaurantCard(r = r, onClick = { onOpenDetails(r.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RestaurantCard(r: Restaurant, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "restaurantScale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .shadow(if (isPressed) 1.dp else 2.dp, RoundedCornerShape(14.dp), ambientColor = Brand.copy(alpha = 0.06f))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Foto do restaurante
            Box(
                modifier = Modifier.size(68.dp).clip(RoundedCornerShape(12.dp))
                    .background(Surface1),
                contentAlignment = Alignment.Center
            ) {
                if (!r.photoUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = r.photoUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text("🍽️", fontSize = 28.sp)
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(r.name, fontFamily = NunitoFamily, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = Ink)
                if (!r.address.isNullOrEmpty()) {
                    Spacer(Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Place, contentDescription = null, tint = InkLight, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(3.dp))
                        Text(r.address, fontFamily = NunitoFamily, fontSize = 12.sp, color = InkLight, maxLines = 1)
                    }
                }
            }

            // Seta
            Box(
                modifier = Modifier.size(32.dp).background(BrandSurface, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("›", fontSize = 20.sp, color = Brand, fontWeight = FontWeight.Bold)
            }
        }
    }
}
