package br.com.boddenb.comidinhas.domain.usecase

import android.location.Location
import android.util.Log
import br.com.boddenb.comidinhas.domain.config.AppConstants
import br.com.boddenb.comidinhas.domain.config.LocationConfig
import br.com.boddenb.comidinhas.domain.model.LatLng
import br.com.boddenb.comidinhas.domain.repository.LocationRepository
import javax.inject.Inject

class GetCurrentLocationUseCase @Inject constructor(
    private val locationRepository: LocationRepository,
    private val locationConfig: LocationConfig
) {
    companion object {
        private const val TAG = "GetCurrentLocationUC"
    }

    suspend operator fun invoke(): LatLng {
        return if (locationConfig.useMockLocation) {
            Log.d(TAG, "Usando localizacao MOCK: Av. Paulista, Sao Paulo")
            locationConfig.mockLocation
        } else {
            Log.d(TAG, "Obtendo localizacao real do dispositivo")
            val location: Location = locationRepository.getCurrentLocation(AppConstants.LOCATION_TIMEOUT_MS)
            LatLng(location.latitude, location.longitude)
        }
    }
}

