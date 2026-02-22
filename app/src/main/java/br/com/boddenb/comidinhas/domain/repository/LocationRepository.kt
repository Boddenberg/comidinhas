package br.com.boddenb.comidinhas.domain.repository

import android.location.Location

interface LocationRepository {
    // Sem timeout por padrão; se o chamador quiser impor um, ele passa explicitamente
    suspend fun getCurrentLocation(timeoutMs: Long = Long.MAX_VALUE): Location
}
