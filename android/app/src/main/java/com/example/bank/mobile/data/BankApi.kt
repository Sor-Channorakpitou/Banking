package com.example.bank.mobile.data

import retrofit2.Call
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
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

    @POST("api/auth/verify-email")
    suspend fun verifyEmail(@Body body: VerifyEmailRequest): Response<Unit>

    @POST("api/users/me/verify-email/resend")
    suspend fun resendVerification(): Response<Unit>

    @POST("api/auth/forgot-password")
    suspend fun forgotPassword(@Body body: ForgotPasswordRequest): Response<Unit>

    @POST("api/auth/reset-password")
    suspend fun resetPassword(@Body body: ResetPasswordRequest): Response<Unit>

    @POST("api/users/me/password")
    suspend fun changePassword(@Body body: ChangePasswordRequest): Response<Unit>

    @POST("api/users/me/two-step/setup")
    suspend fun twoStepSetup(): TwoStepSetup

    @POST("api/users/me/two-step/enable")
    suspend fun twoStepEnable(@Body body: CodeRequest): Response<Unit>

    @POST("api/users/me/two-step/disable")
    suspend fun twoStepDisable(@Body body: CodeRequest): Response<Unit>

    @GET("api/accounts/lookup")
    suspend fun lookup(@Query("number") number: String): Recipient

    @GET("api/payees")
    suspend fun payees(): List<Payee>

    @POST("api/payees")
    suspend fun addPayee(@Body body: PayeeRequest): Payee

    @DELETE("api/payees/{id}")
    suspend fun removePayee(@Path("id") id: Long): Response<Unit>

    @GET("api/accounts/{id}/payment-qr")
    suspend fun paymentQr(@Path("id") id: Long, @Query("amount") amount: String?): PaymentQr

    @POST("api/payment-qr/decode")
    suspend fun decodeQr(@Body body: DecodeQrRequest): PaymentQr

    @GET("api/accounts/{id}/limits")
    suspend fun limits(@Path("id") id: Long): DailyLimit

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
