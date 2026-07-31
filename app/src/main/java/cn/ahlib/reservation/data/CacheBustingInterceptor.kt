package cn.ahlib.reservation.data

import okhttp3.Interceptor
import okhttp3.Response

internal class CacheBustingInterceptor(
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.method != "GET") {
            return chain.proceed(request)
        }

        val url = request.url.newBuilder()
            .setQueryParameter(CACHE_BUSTER_QUERY, currentTimeMillis().toString())
            .build()
        return chain.proceed(request.newBuilder().url(url).build())
    }

    private companion object {
        const val CACHE_BUSTER_QUERY = "_t"
    }
}
