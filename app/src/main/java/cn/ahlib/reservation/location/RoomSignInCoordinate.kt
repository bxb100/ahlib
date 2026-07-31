package cn.ahlib.reservation.location

import cn.ahlib.reservation.data.RoomDetail

internal data class RoomSignInCoordinate(
    val latitude: Double,
    val longitude: Double,
)

internal fun RoomDetail.roomSignInCoordinateOrNull(): RoomSignInCoordinate? {
    val roomLatitude = latitude
        ?.takeIf(Double::isFinite)
        ?.takeIf { value -> value in -90.0..90.0 }
        ?: return null
    val roomLongitude = longitude
        ?.takeIf(Double::isFinite)
        ?.takeIf { value -> value in -180.0..180.0 }
        ?: return null
    return RoomSignInCoordinate(
        latitude = roomLatitude,
        longitude = roomLongitude,
    )
}
