package br.com.boddenb.comidinhas.domain.config

import br.com.boddenb.comidinhas.BuildConfig
import br.com.boddenb.comidinhas.domain.model.LatLng
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationConfig @Inject constructor() {
    val useMockLocation: Boolean = BuildConfig.DEBUG

    val mockLocation: LatLng = LatLng(-23.5613, -46.6563)
}

