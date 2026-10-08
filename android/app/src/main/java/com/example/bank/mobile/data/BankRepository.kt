package com.example.bank.mobile.data

import retrofit2.HttpException
import java.io.IOException
import java.math.BigDecimal
import java.util.UUID

/** The server refused the request; [message] is its explanation (e.g. "Insufficient funds ..."). */
class ApiException(val code: String?, override val message: String, val status: Int) : Exception(message)

/** The server couldn't be reached at all. */
class NetworkException(cause: Throwable) : Exception(cause)

/**
 * The screens' single entry point to the bank. It turns HTTP errors into
 * [ApiException]/[NetworkException] so screens only deal with two kinds of failure.
 */
class BankRepository(private val api: () -> BankApi) {

    suspend fun accounts(): List<Account> = call { api().accounts() }

    suspend fun account(id: Long): Account = call { api().account(id) }

    suspend fun openAccount(currency: String): Account = call { api().openAccount(OpenAccountRequest(currency)) }

    suspend fun history(accountId: Long, size: Int = 50): List<AccountTransaction> =
        call { api().history(accountId, 0, size).content }

    suspend fun statement(accountId: Long, month: String): Statement = call { api().statement(accountId, month) }

    suspend fun rates(): List<ExchangeRate> = call { api().rates() }

    suspend fun quote(from: String, to: String, amount: BigDecimal): ExchangeQuote =
        call { api().quote(from, to, amount.toPlainString()) }

    /**
     * [idempotencyKey]: the screen keeps the same key while the user retries the same
     * transfer, so a retry after a timeout can never send the money twice.
     */
    suspend fun transfer(idempotencyKey: String, request: TransferRequest): TransactionResult =
        call { api().transfer(idempotencyKey, request) }

    suspend fun exchange(idempotencyKey: String, request: ExchangeRequest): ExchangeResult =
        call { api().exchange(idempotencyKey, request) }

    suspend fun me(): UserResponse = call { api().me() }

    suspend fun lookup(accountNumber: String): Recipient = call { api().lookup(accountNumber) }

    suspend fun payees(): List<Payee> = call { api().payees() }

    suspend fun addPayee(accountNumber: String, nickname: String): Payee =
        call { api().addPayee(PayeeRequest(accountNumber, nickname)) }

    suspend fun removePayee(id: Long) = call { api().removePayee(id).orThrow() }

    suspend fun paymentQr(accountId: Long, amount: BigDecimal?): PaymentQr =
        call { api().paymentQr(accountId, amount?.toPlainString()) }

    suspend fun decodeQr(payload: String): PaymentQr = call { api().decodeQr(DecodeQrRequest(payload)) }

    suspend fun limits(accountId: Long): DailyLimit = call { api().limits(accountId) }

    suspend fun resendVerification() = call { api().resendVerification().orThrow() }

    suspend fun changePassword(current: String, new: String) =
        call { api().changePassword(ChangePasswordRequest(current, new)).orThrow() }

    suspend fun twoStepSetup(): TwoStepSetup = call { api().twoStepSetup() }

    suspend fun twoStepEnable(code: String) = call { api().twoStepEnable(CodeRequest(code)).orThrow() }

    suspend fun twoStepDisable(code: String) = call { api().twoStepDisable(CodeRequest(code)).orThrow() }

    companion object {
        fun newIdempotencyKey(): String = UUID.randomUUID().toString()
    }
}

/** Endpoints without a body return Response<Unit>; a non-2xx status becomes an HttpException. */
fun <T> retrofit2.Response<T>.orThrow() {
    if (!isSuccessful) throw HttpException(this)
}

suspend fun <T> call(block: suspend () -> T): T = try {
    block()
} catch (e: HttpException) {
    val problem = runCatching {
        e.response()?.errorBody()?.string()?.let { ApiJson.decodeFromString(Problem.serializer(), it) }
    }.getOrNull()
    val fieldErrors = problem?.errors?.values?.joinToString("\n")
    throw ApiException(
        code = problem?.code,
        message = fieldErrors?.takeIf { it.isNotBlank() } ?: problem?.detail ?: e.message(),
        status = e.code(),
    )
} catch (e: IOException) {
    throw NetworkException(e)
}
