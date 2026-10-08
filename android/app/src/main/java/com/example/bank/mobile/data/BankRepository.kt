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

    companion object {
        fun newIdempotencyKey(): String = UUID.randomUUID().toString()
    }
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
