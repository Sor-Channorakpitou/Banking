package com.example.bank.mobile.data

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonUnquotedLiteral
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigDecimal

// Shapes of the bank API's JSON. Money is always BigDecimal, never Double:
// 0.1 + 0.2 must be exactly 0.3 in a banking app.

typealias Money = @Serializable(with = BigDecimalSerializer::class) BigDecimal

/** Reads JSON numbers exactly and writes them back as plain numbers (100.50, never 1.005E2). */
@OptIn(ExperimentalSerializationApi::class)
object BigDecimalSerializer : KSerializer<BigDecimal> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("BigDecimal", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): BigDecimal =
        if (decoder is JsonDecoder) BigDecimal(decoder.decodeJsonElement().jsonPrimitive.content)
        else BigDecimal(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: BigDecimal) {
        if (encoder is JsonEncoder) encoder.encodeJsonElement(JsonUnquotedLiteral(value.toPlainString()))
        else encoder.encodeString(value.toPlainString())
    }
}

@Serializable data class LoginRequest(val email: String, val password: String)
@Serializable data class RegisterRequest(val fullName: String, val email: String, val password: String)
@Serializable data class RefreshRequest(val refreshToken: String)

@Serializable
data class AuthResponse(
    val accessToken: String,
    val tokenType: String,
    val expiresIn: Long,
    val refreshToken: String,
    val refreshExpiresIn: Long,
)

@Serializable
data class UserResponse(val id: Long, val fullName: String, val email: String, val role: String)

@Serializable
data class Account(
    val id: Long,
    val accountNumber: String,
    val currency: String,
    val status: String,
    val balance: Money,
)

@Serializable data class OpenAccountRequest(val currency: String)

@Serializable
data class AccountTransaction(
    val transactionId: Long,
    val type: String,
    val direction: String,
    val amount: Money,
    val currency: String,
    val description: String? = null,
    val createdAt: String,
) {
    val isCredit: Boolean get() = direction == "CREDIT"
}

@Serializable
data class Page<T>(val content: List<T>, val page: Int, val size: Int, val totalElements: Long, val totalPages: Int)

@Serializable
data class TransferRequest(
    val fromAccountId: Long,
    val toAccountNumber: String,
    val amount: Money,
    val description: String? = null,
)

@Serializable
data class TransactionResult(
    val id: Long,
    val type: String,
    val status: String,
    val amount: Money,
    val currency: String,
    val description: String? = null,
    val fromAccountNumber: String? = null,
    val toAccountNumber: String? = null,
)

@Serializable
data class ExchangeRate(val baseCurrency: String, val quoteCurrency: String, val buyRate: Money, val sellRate: Money)

@Serializable
data class ExchangeQuote(
    val fromCurrency: String,
    val toCurrency: String,
    val amount: Money,
    val convertedAmount: Money,
    val rate: Money,
    val rateBaseCurrency: String,
    val rateQuoteCurrency: String,
)

@Serializable data class ExchangeRequest(val fromAccountId: Long, val toAccountId: Long, val amount: Money)

@Serializable
data class ExchangeResult(
    val transactionId: Long,
    val soldAmount: Money,
    val soldCurrency: String,
    val boughtAmount: Money,
    val boughtCurrency: String,
    val rate: Money,
    val rateBaseCurrency: String,
)

@Serializable
data class Statement(
    val accountNumber: String,
    val currency: String,
    val month: String,
    val openingBalance: Money,
    val totalCredits: Money,
    val totalDebits: Money,
    val closingBalance: Money,
    val lines: List<StatementLine>,
)

@Serializable
data class StatementLine(
    val date: String,
    val transactionId: Long,
    val type: String,
    val description: String? = null,
    val direction: String,
    val amount: Money,
    val balanceAfter: Money,
)

/** The API's error body (RFC 9457 problem details). */
@Serializable
data class Problem(
    val title: String? = null,
    val detail: String? = null,
    val code: String? = null,
    val status: Int? = null,
    val errors: Map<String, String>? = null,
)
