package com.example.bank.mobile.data

import retrofit2.Call
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** The bank API, one function per endpoint. Paths are relative to the server address. */
interface BankApi {

    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequest): AuthResponse

    @POST("api/auth/register")
    suspend fun register(@Body body: RegisterRequest): UserResponse

    /** Blocking variant for OkHttp's Authenticator, which runs on a network thread. */
    @POST("api/auth/refresh")
    fun refreshBlocking(@Body body: RefreshRequest): Call<AuthResponse>

    @POST("api/auth/logout")
    suspend fun logout(@Body body: RefreshRequest): Response<Unit>

    @GET("api/users/me")
    suspend fun me(): UserResponse

    @GET("api/accounts")
    suspend fun accounts(): List<Account>

    @POST("api/accounts")
    suspend fun openAccount(@Body body: OpenAccountRequest): Account

    @GET("api/accounts/{id}")
    suspend fun account(@Path("id") id: Long): Account

    @GET("api/accounts/{id}/transactions")
    suspend fun history(
        @Path("id") id: Long,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 50,
    ): Page<AccountTransaction>

    @GET("api/accounts/{id}/statements/{month}")
    suspend fun statement(@Path("id") id: Long, @Path("month") month: String): Statement

    @POST("api/transfers")
    suspend fun transfer(@Header("Idempotency-Key") key: String, @Body body: TransferRequest): TransactionResult

    @GET("api/exchange-rates")
    suspend fun rates(): List<ExchangeRate>

    @GET("api/exchange-rates/quote")
    suspend fun quote(
        @Query("from") from: String,
        @Query("to") to: String,
        @Query("amount") amount: String,
    ): ExchangeQuote

    @POST("api/exchanges")
    suspend fun exchange(@Header("Idempotency-Key") key: String, @Body body: ExchangeRequest): ExchangeResult
}
