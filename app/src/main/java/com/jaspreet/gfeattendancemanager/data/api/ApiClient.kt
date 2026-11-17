package com.jaspreet.gfeattendancemanager.data.api

import com.jaspreet.gfeattendancemanager.util.KioskLogger
import okhttp3.ConnectionPool
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okio.Buffer
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.UUID
import java.util.concurrent.TimeUnit

object ApiClient {
    @Volatile
    private var currentBaseUrl: String? = null

    @Volatile
    private var retrofit: Retrofit? = null

    @Volatile
    private var apiService: KioskApiService? = null

    private val okHttpClient by lazy {
        val customLogInterceptor = Interceptor { chain ->
            val originalRequest = chain.request()
            val method = originalRequest.method

            // Add Idempotency key to POST/PUT requests if not present
            val requestBuilder = originalRequest.newBuilder()
            if ((method.equals("POST", ignoreCase = true) || method.equals("PUT", ignoreCase = true)) &&
                originalRequest.header("X-Idempotency-Key") == null
            ) {
                requestBuilder.header("X-Idempotency-Key", UUID.randomUUID().toString())
            }

            val request = requestBuilder.build()
            val url = request.url

            KioskLogger.api("[API REQUEST] $method $url")

            val requestBody = request.body
            if (requestBody != null) {
                try {
                    val buffer = Buffer()
                    requestBody.writeTo(buffer)
                    val charset = requestBody.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
                    val requestBodyString = buffer.readString(charset)
                    if (requestBodyString.isNotEmpty()) {
                        KioskLogger.api("[API REQUEST BODY] $requestBodyString")
                    }
                } catch (_: Exception) {
                    // Ignore request body reading errors safely
                }
            }

            val startNs = System.nanoTime()
            try {
                val response = chain.proceed(request)
                val durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs)

                val responseBody = response.body
                val responseBodyString = try {
                    val source = responseBody.source()
                    source.request(Long.MAX_VALUE)
                    val buffer = source.buffer
                    buffer.clone().readString(Charsets.UTF_8)
                } catch (_: Exception) {
                    null
                }

                if (response.isSuccessful) {
                    KioskLogger.api("[API RESPONSE ${response.code}] $method $url (${durationMs}ms)")
                    if (!responseBodyString.isNullOrEmpty()) {
                        KioskLogger.api("[API RESPONSE BODY] $responseBodyString")
                    }
                } else {
                    KioskLogger.error("[API ERROR ${response.code}] $method $url (${durationMs}ms)")
                    if (!responseBodyString.isNullOrEmpty()) {
                        KioskLogger.error("[API ERROR BODY] $responseBodyString")
                    }
                }
                response
            } catch (e: Exception) {
                val failMsg = "[API FAILED] $method $url - Error: ${e.message}"
                KioskLogger.error(failMsg, e)
                throw e
            }
        }
        OkHttpClient.Builder()
            .protocols(listOf(Protocol.HTTP_1_1, Protocol.HTTP_2))
            .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
            .addInterceptor(customLogInterceptor)
            .connectTimeout(45, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(45, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    @Synchronized
    fun getService(baseUrl: String): KioskApiService {
        val formattedUrl = formatBaseUrl(baseUrl)
        if (apiService == null || currentBaseUrl != formattedUrl) {
            currentBaseUrl = formattedUrl
            val newRetrofit = Retrofit.Builder()
                .baseUrl(formattedUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            retrofit = newRetrofit
            apiService = newRetrofit.create(KioskApiService::class.java)
        }
        return apiService!!
    }

    @Synchronized
    fun updateBaseUrl(baseUrl: String) {
        val formattedUrl = formatBaseUrl(baseUrl)
        currentBaseUrl = formattedUrl
        val newRetrofit = Retrofit.Builder()
            .baseUrl(formattedUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        retrofit = newRetrofit
        apiService = newRetrofit.create(KioskApiService::class.java)
    }

    fun validateBaseUrl(baseUrl: String): String {
        return formatBaseUrl(baseUrl)
    }

    private fun formatBaseUrl(baseUrl: String): String {
        var formatted = baseUrl.trim()
        if (formatted.isBlank()) {
            formatted = "https://galaxyerp.vercel.app"
        }
        if (!formatted.startsWith("http://") && !formatted.startsWith("https://")) {
            formatted = "https://$formatted"
        }
        if (!formatted.endsWith("/")) {
            formatted = "$formatted/"
        }

        // Validate via HttpUrl
        val parsed = formatted.toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Invalid URL format: $baseUrl")
        return parsed.toString()
    }
}
