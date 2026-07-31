package cn.ahlib.reservation.data

data class Captcha(
    val img: String = "",
    val uniCode: String = "",
)

data class UserInfo(
    val id: String = "",
    val token: String? = null,
    val name: String? = null,
    val mobile: String? = null,
    val mobileStatus: String? = null,
    val readerStatus: String? = null,
    val idCard: String? = null,
    val gender: String? = null,
    val bindInfo: String? = null,
)

data class WechatConfig(
    val appId: String = "",
    val timestamp: Long = 0L,
    val nonceStr: String = "",
    val signature: String = "",
    val jsApiList: List<String> = emptyList(),
)

data class Category(
    val id: String = "",
    val categoryName: String = "",
    val idModel: String? = null,
    val categoryType: String? = null,
    val contentTemplate: String? = null,
    val listTemplate: String? = null,
    val parentId: String? = null,
    val childList: List<Category> = emptyList(),
)

data class RoomSummary(
    val id: String = "",
    val roomName: String = "",
    val venueName: String? = null,
    val address: String? = null,
    val coverUrl: String? = null,
    val isTop: Int? = null,
    val totalNum: Int? = null,
    val ableNum: Int? = null,
    val distance: Double? = null,
)

data class RoomDetail(
    val id: String = "",
    val roomName: String = "",
    val venueName: String? = null,
    val address: String? = null,
    val coverUrl: String? = null,
    val isTop: Int? = null,
    val publishTime: String? = null,
    val viewNum: Int? = null,
    val keyword: String? = null,
    val phoneNum: String? = null,
    val roomArea: Double? = null,
    val totalNum: Int? = null,
    val ableNum: Int? = null,
    val defaultBookingDays: Int? = null,
    val ableNums: Int? = null,
    val introduction: String? = null,
    val appointRule: String? = null,
    val distance: Double? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

data class RoomPage(
    val result: List<RoomSummary> = emptyList(),
    val totalPages: Int = 0,
    val total: Int = 0,
    val pageNum: Int = 1,
    val pageSize: Int = 0,
)

data class AvailabilitySlot(
    val id: String = "",
    val bookDate: String? = null,
    val startTime: String = "",
    val endTime: String = "",
    val leftNum: Int? = null,
    val isOpen: Int? = null,
    val bookFlag: Int? = null,
    val bookingStatusName: String? = null,
)

data class AvailabilityDay(
    val date: String = "",
    val bookDate: String? = null,
    val list: List<AvailabilitySlot> = emptyList(),
    val isOpen: Int? = null,
    val totalLeftNum: Int? = null,
)

data class CreateReservationRequest(
    val venueName: String,
    val dateTime: String,
    val bookingId: String,
    val userName: String? = null,
    val phoneNum: String? = null,
)

data class AppointmentRecord(
    val id: String = "",
    val bookingId: String = "",
    val signState: Int? = null,
    val status: Int? = null,
    val statusMerge: Int? = null,
    val statusMergeName: String? = null,
    val roomName: String? = null,
    val venueName: String? = null,
    val bookNum: Int? = null,
    val userName: String? = null,
    val phoneNum: String? = null,
    val bookDate: String? = null,
    val dateTime: String? = null,
    val startTime: String? = null,
    val endTime: String? = null,
    val createTime: String? = null,
    val canSign: Int? = null,
)

data class ReservationPage(
    val result: List<AppointmentRecord> = emptyList(),
    val totalPages: Int = 0,
    val total: Int = 0,
    val pageNum: Int = 1,
    val pageSize: Int = 0,
)

data class RoomSignRequest(
    val id: String,
    val bookingId: String,
    val latitude: String = "",
    val longitude: String = "",
) {
    constructor(
        id: String,
        bookingId: String,
        latitude: Double,
        longitude: Double,
    ) : this(
        id = id,
        bookingId = bookingId,
        latitude = latitude.toString(),
        longitude = longitude.toString(),
    )
}

data class RoomSignOffRequest(
    val id: String,
    val bookingId: String,
)

internal data class LoginRequest(
    val readerId: String,
    val userPassword: String,
    val verifyCode: String,
    val uniCode: String,
    val loginTime: Int,
)

internal data class UpdatePhoneRequest(
    val mobileCode: String,
    val mobile: String,
)

internal data class CancelReservationRequest(
    val id: String,
)

internal data class ApiEnvelope<T>(
    val code: Int = 0,
    val errorMsg: String? = null,
    val message: String? = null,
    val msg: String? = null,
    val data: T? = null,
) {
    val resolvedMessage: String?
        get() = errorMsg?.takeIf(String::isNotBlank)
            ?: message?.takeIf(String::isNotBlank)
            ?: msg?.takeIf(String::isNotBlank)
}
