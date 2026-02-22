package br.com.boddenb.comidinhas.domain.mapper

import br.com.boddenb.comidinhas.domain.config.AppConstants
import br.com.boddenb.comidinhas.domain.model.Restaurant
import javax.inject.Inject

class RestaurantToAiInfoMapper @Inject constructor() {

    fun map(restaurant: Restaurant): String = buildString {
        append(formatBasicInfo(restaurant))
        append(formatTypes(restaurant))
        append(formatDescription(restaurant))
        append(formatReviews(restaurant))
    }

    private fun formatBasicInfo(r: Restaurant): String {
        val ratingStr = r.rating?.let { "★%.1f".format(it) } ?: "sem rating"
        val priceStr = formatPriceLevel(r.priceLevel)
        val addressShort = r.address?.take(AppConstants.MAX_ADDRESS_LENGTH) ?: "endereço?"

        return "${r.name} | $addressShort | $ratingStr | $priceStr"
    }

    private fun formatPriceLevel(level: Int?): String = when (level) {
        0, 1 -> "💰"
        2 -> "💰💰"
        3 -> "💰💰💰"
        4 -> "💰💰💰💰"
        else -> "preço?"
    }

    private fun formatTypes(r: Restaurant): String {
        val typesStr = r.types?.joinToString(", ") { type ->
            type.replace("_", " ").lowercase()
        } ?: ""

        return if (typesStr.isNotEmpty()) {
            " | Tipos: $typesStr"
        } else ""
    }

    private fun formatDescription(r: Restaurant): String {
        val description = r.editorialSummary ?: ""
        return if (description.isNotEmpty()) {
            " | Descrição: $description"
        } else ""
    }

    private fun formatReviews(r: Restaurant): String {
        val reviewsText = r.reviews
            ?.take(AppConstants.MAX_REVIEWS_TO_SHOW)
            ?.joinToString(" | ") { review ->
                review.text.take(AppConstants.MAX_REVIEW_TEXT_LENGTH)
            } ?: ""

        return if (reviewsText.isNotEmpty()) {
            " | Reviews: $reviewsText"
        } else ""
    }
}

