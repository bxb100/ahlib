package cn.ahlib.reservation.data

import com.google.gson.JsonElement
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

internal interface ReservationApi {
    @GET("api-server/pc/pc_pass/createVerify")
    suspend fun getCaptcha(
        @Query("w") width: Int,
        @Query("h") height: Int,
        @Query("ls") lineSpacing: Int,
    ): ApiEnvelope<Captcha>

    @POST("api-server/pc/pc_pass/login")
    suspend fun login(@Body request: LoginRequest): ApiEnvelope<UserInfo>

    @GET("api-server/pc/pc_pass/isLogin")
    suspend fun isLoggedIn(): ApiEnvelope<Boolean>

    @GET("api-server/pc/pc_pass/logout")
    suspend fun logout(): ApiEnvelope<JsonElement>

    @GET("api-server/pc/pc_pass/sendMessageCode")
    suspend fun sendMessageCode(
        @Query("mobile") mobile: String,
        @Query("verifyCode") verifyCode: String,
        @Query("uniCode") uniCode: String,
    ): ApiEnvelope<JsonElement>

    @POST("api-server/pc/update/phoneNum")
    suspend fun updatePhone(@Body request: UpdatePhoneRequest): ApiEnvelope<JsonElement>

    @GET("api-server/pc/userInfo")
    suspend fun getUserInfo(): ApiEnvelope<JsonElement>

    @GET("api-server/pc/pc_pass/getWxConfig")
    suspend fun getWechatConfig(
        @Query("url") pageUrl: String,
    ): ApiEnvelope<WechatConfig>

    @GET("api-server/pc_pass/category/pcSelectList")
    suspend fun getCategories(
        @Query("siteCode") siteCode: String?,
    ): ApiEnvelope<List<Category>>

    @GET("api-server/pc/room/pc_pass/roomDataPage")
    suspend fun getRooms(
        @Query("pageNum") pageNum: Int,
        @Query("pageSize") pageSize: Int,
        @Query("idCategory") categoryId: String,
        @Query("resourcesType") resourcesType: String,
        @Query("keywords") keywords: String,
        @Query("year") year: String?,
        @Query("total") total: Int,
    ): ApiEnvelope<RoomPage>

    @GET("api-server/pc/room/pc_pass/roomDetail")
    suspend fun getRoomDetail(@Query("id") roomId: String): ApiEnvelope<RoomDetail>

    @GET("api-server/pc/room/pc_pass/roomBookDetail/{roomId}")
    suspend fun getRoomAvailability(
        @Path("roomId") roomId: String,
    ): ApiEnvelope<List<AvailabilityDay>>

    @POST("api-server/pc/room/appoint")
    suspend fun createReservation(
        @Body request: CreateReservationRequest,
    ): ApiEnvelope<JsonElement>

    @GET("api-server/pc/room/appointDataPage")
    suspend fun getMyReservations(
        @Query("pageNum") pageNum: Int,
        @Query("pageSize") pageSize: Int,
        @Query("type") type: String,
        @Query("total") total: Int,
    ): ApiEnvelope<ReservationPage>

    @POST("api-server/pc/room/cancelAppoint")
    suspend fun cancelReservation(
        @Body request: CancelReservationRequest,
    ): ApiEnvelope<JsonElement>

    @GET("api-server/pc/room/getOneAppointRecord/{roomId}")
    suspend fun getCurrentReservation(
        @Path("roomId") roomId: String,
    ): ApiEnvelope<AppointmentRecord>

    @POST("api-server/pc/room/roomSign")
    suspend fun roomSign(@Body request: RoomSignRequest): ApiEnvelope<JsonElement>

    @PUT("api-server/pc/room/roomSignOff")
    suspend fun roomSignOff(@Body request: RoomSignOffRequest): ApiEnvelope<JsonElement>
}
