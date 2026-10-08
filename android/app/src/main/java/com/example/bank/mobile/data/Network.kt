package com.example.bank.mobile.data

import com.example.bank.mobile.BuildConfig
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

val ApiJson = Json {
    ignoreUnknownKeys = true // the server may add fields; old app versions must keep working
    explicitNulls = false
}

/** The server can be changed on the login screen, so requests are pointed at it at send time. */
class ServerAddress(initial: String) {
    @Volatile
    var url: HttpUrl = initial.toHttpUrl()
        private set

    fun set(value: String) {
        val normalized = value.trim().let { if (it.endsWith("/")) it else "$it/" }
        url = normalized.toHttpUrl()
    }
}

private class ServerAddressInterceptor(private val server: ServerAddress) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val target = server.url
        val original = chain.request().url
        val url = original.newBuilder()
            .scheme(target.scheme)
            .host(target.host)
            .port(target.port)
            .encodedPath(target.encodedPath.trimEnd('/') + original.encodedPath)
            .build()
        return chain.proceed(chain.request().newBuilder().url(url).build())
    }
}

/** Adds "Authorization: Bearer <access token>" to every request once signed in. */
private class AccessTokenInterceptor(private val tokens: () -> String?) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokens() ?: return chain.proceed(chain.request())
        return chain.proceed(chain.request().newBuilder().header("Authorization", "Bearer $token").build())
    }
}

/**
 * Access tokens live 15 minutes. When one expires the server answers 401; OkHttp then
 * asks this class for a retry. It swaps the refresh token for a new access token and
 * repeats the request once, so screens never notice.
 */
private class RefreshingAuthenticator(private val session: () -> SessionManager) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.request.url.encodedPath.contains("/api/auth/")) return null
        if (response.priorResponse != null) return null // already retried once
        val used = response.request.header("Authorization")?.removePrefix("Bearer ")
        val fresh = runBlocking { session().refreshAccessToken(used) } ?: return null
        return response.request.newBuilder().header("Authorization", "Bearer $fresh").build()
    }
}

class Network(server: ServerAddress, accessToken: () -> String?, session: () -> SessionManager) {

    private val base: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(ServerAddressInterceptor(server))
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC // never BODY: it would log tokens
                })
            }
        }
        .build()

    /** No token, no retry: used for sign-in and for refreshing itself. */
    val publicApi: BankApi = retrofit(base)

    val api: BankApi = retrofit(
        base.newBuilder()
            .addInterceptor(AccessTokenInterceptor(accessToken))
            .authenticator(RefreshingAuthenticator(session))
            .build(),
    )

    private fun retrofit(client: OkHttpClient): BankApi = Retrofit.Builder()
        .baseUrl("http://placeholder.invalid/") // replaced per request by ServerAddressInterceptor
        .client(client)
        .addConverterFactory(ApiJson.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(BankApi::class.java)
}
