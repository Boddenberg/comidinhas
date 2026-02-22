package br.com.boddenb.comidinhas.ui.screen.eatout

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.rememberAsyncImagePainter
import java.io.File

@Composable
fun EatOutDetailsRoute(id: String, onBack: () -> Unit, vm: ComerForaViewModel = hiltViewModel()) {
    EatOutDetailsScreen(id = id, vm = vm, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EatOutDetailsScreen(id: String, vm: ComerForaViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val r = vm.findById(id)

    if (r == null) {
        Scaffold { padding ->
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🍽️", fontSize = 48.sp)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Restaurante não encontrado",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF666666)
                    )
                }
            }
        }
        return
    }

    Scaffold(
        containerColor = Color(0xFFFFFBF7)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Header simplificado com foto
            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Foto do restaurante
                if (r.photoUrl != null) {
                    Image(
                        painter = rememberAsyncImagePainter(model = File(r.photoUrl)),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .background(Color(0xFFF5F5F5)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🍽️", fontSize = 64.sp)
                    }
                }

                // Botão voltar minimalista
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                        .size(40.dp)
                        .shadow(2.dp, CircleShape)
                        .background(Color.White.copy(alpha = 0.95f), CircleShape)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Voltar",
                        tint = Color(0xFF2C1810),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Card de informações principais (sobreposto à foto)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = (-30).dp)
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    // Nome
                    Text(
                        r.name,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2C1810),
                        lineHeight = 30.sp
                    )

                    Spacer(Modifier.height(12.dp))

                    // Rating, badges e botões de contato
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Badges (Rating, Status, Preço)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            // Rating
                            if (r.rating != null) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFFFF3E0)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("⭐", fontSize = 14.sp)
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            "%.1f".format(r.rating),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFFF6B35)
                                        )
                                    }
                                }
                            }

                            // Status
                            if (r.isOpenNow == true) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFE8F5E9)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .background(Color(0xFF4CAF50), CircleShape)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            "Aberto",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFF2E7D32)
                                        )
                                    }
                                }
                            }

                            // Preço
                            if (r.priceLevel != null && r.priceLevel > 0) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFF5F5F5)
                                ) {
                                    Text(
                                        toPriceSymbols(r.priceLevel),
                                        fontSize = 13.sp,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        color = Color(0xFF666666),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        // Botões de contato elegantes
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Botão Telefone
                            if (!r.phoneNumber.isNullOrEmpty()) {
                                Button(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${r.phoneNumber}"))
                                        context.startActivity(intent)
                                    },
                                    modifier = Modifier
                                        .height(36.dp)
                                        .defaultMinSize(minWidth = 0.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF4CAF50)
                                    ),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    Text(
                                        "Ligar",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            // Botão Website
                            if (!r.website.isNullOrEmpty()) {
                                OutlinedButton(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(r.website))
                                        context.startActivity(intent)
                                    },
                                    modifier = Modifier
                                        .height(36.dp)
                                        .defaultMinSize(minWidth = 0.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = Color(0xFF2196F3)
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.5.dp,
                                        Color(0xFF2196F3)
                                    ),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    Text(
                                        "Site",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Conteúdo com offset negativo para sobrepor
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .offset(y = (-20).dp)
            ) {
                // Card de Endereço (minimalista)
                if (!r.address.isNullOrEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("📍", fontSize = 18.sp)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Localização",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF2C1810)
                                )
                            }

                            Spacer(Modifier.height(12.dp))

                            Text(
                                r.address,
                                fontSize = 14.sp,
                                color = Color(0xFF666666),
                                lineHeight = 20.sp
                            )

                            Spacer(Modifier.height(12.dp))

                            Button(
                                onClick = {
                                    val uri = Uri.parse("geo:${r.latLng.latitude},${r.latLng.longitude}?q=${Uri.encode(r.name)}")
                                    val intent = Intent(Intent.ACTION_VIEW, uri)
                                    intent.setPackage("com.google.android.apps.maps")
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF2196F3)
                                ),
                                contentPadding = PaddingValues(vertical = 12.dp)
                            ) {
                                Text(
                                    "Como Chegar",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                }

                // Card de Horário (minimalista)
                if (!r.openingHours.isNullOrEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🕐", fontSize = 18.sp)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Horário",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF2C1810)
                                )
                            }

                            Spacer(Modifier.height(12.dp))

                            r.openingHours.forEachIndexed { index, daySchedule ->
                                Text(
                                    daySchedule,
                                    fontSize = 13.sp,
                                    color = Color(0xFF666666),
                                    lineHeight = 20.sp,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                }

                // Card de Avaliações (design moderno)
                if (!r.reviews.isNullOrEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("💬", fontSize = 18.sp)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "Avaliações",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF2C1810)
                                    )
                                }
                                Text(
                                    "${r.reviews.size} reviews",
                                    fontSize = 12.sp,
                                    color = Color(0xFF999999)
                                )
                            }

                            Spacer(Modifier.height(16.dp))

                            r.reviews.forEachIndexed { index, review ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Avatar minimalista
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .background(Color(0xFFF5F5F5), CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    review.authorName.take(1).uppercase(),
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF666666)
                                                )
                                            }
                                            Spacer(Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    review.authorName,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color(0xFF2C1810)
                                                )
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    repeat(5) { starIndex ->
                                                        Text(
                                                            if (starIndex < review.rating.toInt()) "⭐" else "☆",
                                                            fontSize = 11.sp,
                                                            color = if (starIndex < review.rating.toInt()) Color(0xFFFFB300) else Color(0xFFDDDDDD)
                                                        )
                                                    }
                                                    Spacer(Modifier.width(6.dp))
                                                    Text(
                                                        review.relativeTime,
                                                        fontSize = 11.sp,
                                                        color = Color(0xFF999999)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (review.text.isNotEmpty()) {
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            review.text,
                                            fontSize = 13.sp,
                                            color = Color(0xFF666666),
                                            lineHeight = 19.sp
                                        )
                                    }
                                }

                                if (index < r.reviews.size - 1) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        color = Color(0xFFF0F0F0)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                }

                // Card de Informações (grid minimalista)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("ℹ️", fontSize = 18.sp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Informações",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF2C1810)
                            )
                        }

                        Spacer(Modifier.height(16.dp))

                        // Grid 2x2 de informações
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Avaliação
                                if (r.rating != null) {
                                    InfoCard(
                                        icon = "⭐",
                                        label = "Avaliação",
                                        value = "%.1f".format(r.rating),
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                // Total de avaliações
                                if (r.ratingsCount != null && r.ratingsCount > 0) {
                                    InfoCard(
                                        icon = "👥",
                                        label = "Reviews",
                                        value = "${r.ratingsCount}",
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Faixa de preço
                                if (r.priceLevel != null && r.priceLevel > 0) {
                                    InfoCard(
                                        icon = "💰",
                                        label = "Preço",
                                        value = toPriceSymbols(r.priceLevel),
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                // Status
                                InfoCard(
                                    icon = if (r.isOpenNow == true) "🟢" else "🔴",
                                    label = "Status",
                                    value = if (r.isOpenNow == true) "Aberto" else "Fechado",
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun InfoCard(
    icon: String,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFF8F8F8)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(icon, fontSize = 24.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                value,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2C1810)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                label,
                fontSize = 11.sp,
                color = Color(0xFF999999),
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// Helper to convert price level to emoji symbols (avoids using $ in strings)
fun toPriceSymbols(level: Int?): String {
    if (level == null || level <= 0) return ""
    val clamped = level.coerceIn(1, 4)
    return "💰".repeat(clamped)
}
