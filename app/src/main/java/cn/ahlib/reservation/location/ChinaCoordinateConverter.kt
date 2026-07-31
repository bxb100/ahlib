package cn.ahlib.reservation.location

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class GeoCoordinate(
    val latitude: Double,
    val longitude: Double,
)

object ChinaCoordinateConverter {
    fun wgs84ToGcj02(
        latitude: Double,
        longitude: Double,
    ): GeoCoordinate {
        if (isOutsideMainlandChina(latitude, longitude)) {
            return GeoCoordinate(latitude, longitude)
        }

        var latitudeOffset = transformLatitude(
            longitude - REFERENCE_LONGITUDE,
            latitude - REFERENCE_LATITUDE,
        )
        var longitudeOffset = transformLongitude(
            longitude - REFERENCE_LONGITUDE,
            latitude - REFERENCE_LATITUDE,
        )
        val radians = latitude / 180.0 * PI
        var magic = sin(radians)
        magic = 1 - ECCENTRICITY_SQUARED * magic * magic
        val squareRootMagic = sqrt(magic)
        latitudeOffset = latitudeOffset * 180.0 /
            (
                (
                    SEMI_MAJOR_AXIS * (1 - ECCENTRICITY_SQUARED) /
                        (magic * squareRootMagic)
                    ) * PI
                )
        longitudeOffset = longitudeOffset * 180.0 /
            (SEMI_MAJOR_AXIS / squareRootMagic * cos(radians) * PI)

        return GeoCoordinate(
            latitude = latitude + latitudeOffset,
            longitude = longitude + longitudeOffset,
        )
    }

    private fun isOutsideMainlandChina(
        latitude: Double,
        longitude: Double,
    ): Boolean =
        longitude !in MIN_LONGITUDE..MAX_LONGITUDE ||
            latitude !in MIN_LATITUDE..MAX_LATITUDE

    private fun transformLatitude(
        longitude: Double,
        latitude: Double,
    ): Double {
        var result = -100.0 +
            2.0 * longitude +
            3.0 * latitude +
            0.2 * latitude * latitude +
            0.1 * longitude * latitude +
            0.2 * sqrt(kotlin.math.abs(longitude))
        result += (
            20.0 * sin(6.0 * longitude * PI) +
                20.0 * sin(2.0 * longitude * PI)
            ) * 2.0 / 3.0
        result += (
            20.0 * sin(latitude * PI) +
                40.0 * sin(latitude / 3.0 * PI)
            ) * 2.0 / 3.0
        result += (
            160.0 * sin(latitude / 12.0 * PI) +
                320.0 * sin(latitude * PI / 30.0)
            ) * 2.0 / 3.0
        return result
    }

    private fun transformLongitude(
        longitude: Double,
        latitude: Double,
    ): Double {
        var result = 300.0 +
            longitude +
            2.0 * latitude +
            0.1 * longitude * longitude +
            0.1 * longitude * latitude +
            0.1 * sqrt(kotlin.math.abs(longitude))
        result += (
            20.0 * sin(6.0 * longitude * PI) +
                20.0 * sin(2.0 * longitude * PI)
            ) * 2.0 / 3.0
        result += (
            20.0 * sin(longitude * PI) +
                40.0 * sin(longitude / 3.0 * PI)
            ) * 2.0 / 3.0
        result += (
            150.0 * sin(longitude / 12.0 * PI) +
                300.0 * sin(longitude / 30.0 * PI)
            ) * 2.0 / 3.0
        return result
    }

    private const val SEMI_MAJOR_AXIS = 6_378_245.0
    private const val ECCENTRICITY_SQUARED = 0.006693421622965943
    private const val REFERENCE_LONGITUDE = 105.0
    private const val REFERENCE_LATITUDE = 35.0
    private const val MIN_LONGITUDE = 72.004
    private const val MAX_LONGITUDE = 137.8347
    private const val MIN_LATITUDE = 0.8293
    private const val MAX_LATITUDE = 55.8271
}
