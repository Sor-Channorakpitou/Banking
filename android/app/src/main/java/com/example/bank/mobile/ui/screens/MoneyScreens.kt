package com.example.bank.mobile.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bank.mobile.R
import com.example.bank.mobile.container
import com.example.bank.mobile.data.Account
import com.example.bank.mobile.data.BankRepository
import com.example.bank.mobile.data.ExchangeQuote
import com.example.bank.mobile.data.ExchangeRequest
import com.example.bank.mobile.data.Statement
import com.example.bank.mobile.data.TransferRequest
import com.example.bank.mobile.ui.cleanAmountInput
import com.example.bank.mobile.ui.components.ErrorText
import com.example.bank.mobile.ui.components.FieldLabel
import com.example.bank.mobile.ui.components.InnerScreen
import com.example.bank.mobile.ui.components.LimeCard
import com.example.bank.mobile.ui.components.LimeTextField
import com.example.bank.mobile.ui.components.LoadingBox
import com.example.bank.mobile.ui.components.PrimaryButton
import com.example.bank.mobile.ui.components.RowDivider
import com.example.bank.mobile.ui.components.SecondaryButton
import com.example.bank.mobile.ui.components.SummaryRow
import com.example.bank.mobile.ui.components.TransactionRow
import com.example.bank.mobile.ui.currencyDecimals
import com.example.bank.mobile.ui.currencySymbol
import com.example.bank.mobile.ui.formatAccountNumber
import com.example.bank.mobile.ui.formatMoney
import com.example.bank.mobile.ui.formatRate
import com.example.bank.mobile.ui.formatSigned
import com.example.bank.mobile.ui.localDate
import com.example.bank.mobile.ui.parseAmount
import com.example.bank.mobile.ui.shortDate
import com.example.bank.mobile.ui.theme.Lime
import com.example.bank.mobile.ui.theme.MoneyStyle
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.format.DateTimeFormatter

// ------------------------------------------------------------------ shared pieces

/** Tappable card showing an account; opens a menu to pick another. */
@Composable
private fun AccountPicker(
    label: String,
    accounts: List<Account>,
    selected: Account?,
    onSelect: (Account) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Column {
        FieldLabel(label)
        Box {
            LimeCard(Modifier.fillMaxWidth().clickable(role = Role.DropdownList) { open = true }) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            selected?.let { stringResource(R.string.account_label, it.currency) } ?: "—",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        selected?.let { Text(formatAccountNumber(it.accountNumber), style = MoneyStyle.Tiny, color = Lime.Muted) }
                    }
                    selected?.let { Text(formatMoney(it.balance, it.currency), style = MoneyStyle.Small) }
                }
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                accounts.forEach { account ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                "${account.currency} · ${formatAccountNumber(account.accountNumber)} · " +
                                    formatMoney(account.balance, account.currency),
                            )
                        },
                        onClick = { onSelect(account); open = false },
                    )
                }
            }
        }
    }
}

/** Shown after money moved: a big tick and what happened. */
@Composable
private fun ColumnScope.SuccessPanel(title: String, body: String, onDone: () -> Unit) {
    Column(
        Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(84.dp).clip(CircleShape).background(Lime.GreenTint), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_check), null, tint = Lime.Green, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyLarge, color = Lime.Muted, textAlign = TextAlign.Center)
    }
    PrimaryButton(stringResource(R.string.done), onDone, Modifier.padding(20.dp).navigationBarsPadding())
}

// ------------------------------------------------------------------ transfer

class TransferViewModel(private val repository: BankRepository) : ViewModel() {
    var accounts by mutableStateOf<List<Account>>(emptyList()); private set
    var from by mutableStateOf<Account?>(null)
    var toNumber by mutableStateOf("")
    var amountText by mutableStateOf("")
    var description by mutableStateOf("")
    var busy by mutableStateOf(false); private set
    var error by mutableStateOf<Throwable?>(null); private set
    var sent by mutableStateOf<String?>(null); private set

    /**
     * Kept while the user retries the same transfer (e.g. after a timeout) and only
     * replaced when they change something, so a retry can never pay twice.
     */
    private var idempotencyKey = BankRepository.newIdempotencyKey()

    fun load(preferredId: Long?) = viewModelScope.launch {
        try {
            accounts = repository.accounts().filter { it.status == "ACTIVE" }
            if (from == null) from = accounts.firstOrNull { it.id == preferredId } ?: accounts.firstOrNull()
        } catch (e: Exception) {
            error = e
        }
    }

    fun edited() {
        idempotencyKey = BankRepository.newIdempotencyKey()
        error = null
    }

    fun send() = viewModelScope.launch {
        val account = from ?: return@launch
        val amount = parseAmount(amountText) ?: return@launch
        busy = true
        error = null
        try {
            val result = repository.transfer(
                idempotencyKey,
                TransferRequest(account.id, toNumber, amount, description.ifBlank { null }),
            )
            sent = formatMoney(result.amount, result.currency) + "|" + formatAccountNumber(result.toAccountNumber ?: toNumber)
        } catch (e: Exception) {
            error = e
        } finally {
            busy = false
        }
    }
}

@Composable
fun TransferScreen(fromAccountId: Long?, onBack: () -> Unit) {
    val container = LocalContext.current.container
    val vm: TransferViewModel = viewModel { TransferViewModel(container.repository) }
    LaunchedEffect(Unit) { vm.load(fromAccountId) }
    val from = vm.from
    val amount = parseAmount(vm.amountText)

    InnerScreen(stringResource(R.string.transfer_title), onBack) {
        val sent = vm.sent
        if (sent != null) {
            val (money, to) = sent.split("|")
            SuccessPanel(stringResource(R.string.transfer_done), stringResource(R.string.transfer_done_body, money, to), onBack)
            return@InnerScreen
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AccountPicker(stringResource(R.string.from), vm.accounts, from) { vm.from = it; vm.edited() }
            LimeTextField(
                vm.toNumber,
                { vm.toNumber = it.filter(Char::isDigit).take(11); vm.edited() },
                stringResource(R.string.to_account_number),
                keyboardType = KeyboardType.Number,
                textStyle = MoneyStyle.Medium,
            )
            LimeTextField(
                vm.amountText,
                { vm.amountText = cleanAmountInput(it, currencyDecimals(from?.currency ?: "USD")); vm.edited() },
                stringResource(R.string.amount),
                keyboardType = KeyboardType.Decimal,
                textStyle = MoneyStyle.Large,
                prefix = currencySymbol(from?.currency ?: "USD"),
            )
            LimeTextField(vm.description, { vm.description = it.take(255); vm.edited() },
                stringResource(R.string.description_optional))

            if (from != null && amount != null) {
                LimeCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                    SummaryRow(stringResource(R.string.fee), stringResource(R.string.fee_none))
                    RowDivider()
                    SummaryRow(stringResource(R.string.balance_after), formatMoney(from.balance - amount, from.currency), MoneyStyle.Small)
                }
            }
            vm.error?.let { ErrorText(errorMessage(it)) }
        }
        PrimaryButton(
            text = stringResource(R.string.send_amount, amount?.let { formatMoney(it, from?.currency ?: "USD") } ?: ""),
            onClick = { vm.send() },
            enabled = from != null && amount != null && vm.toNumber.length == 11,
            loading = vm.busy,
            modifier = Modifier.padding(20.dp).navigationBarsPadding(),
        )
    }
}

// ------------------------------------------------------------------ exchange

class ExchangeViewModel(private val repository: BankRepository) : ViewModel() {
    var accounts by mutableStateOf<List<Account>>(emptyList()); private set
    var from by mutableStateOf<Account?>(null); private set
    var to by mutableStateOf<Account?>(null); private set
    var amountText by mutableStateOf("")
    var quote by mutableStateOf<ExchangeQuote?>(null); private set
    var busy by mutableStateOf(false); private set
    var loaded by mutableStateOf(false); private set
    var error by mutableStateOf<Throwable?>(null); private set
    var done by mutableStateOf<String?>(null); private set
    private var idempotencyKey = BankRepository.newIdempotencyKey()

    fun load() = viewModelScope.launch {
        try {
            accounts = repository.accounts().filter { it.status == "ACTIVE" }
            if (from == null) {
                from = accounts.firstOrNull { it.currency == "USD" }
                to = accounts.firstOrNull { it.currency == "KHR" }
            }
        } catch (e: Exception) {
            error = e
        } finally {
            loaded = true
        }
    }

    fun swap() {
        val f = from
        from = to
        to = f
        amountText = ""
        quote = null
        edited()
    }

    fun edited() {
        idempotencyKey = BankRepository.newIdempotencyKey()
        error = null
    }

    /** Asks the server what this amount gives right now (called after the user stops typing). */
    suspend fun refreshQuote() {
        val f = from ?: return
        val t = to ?: return
        val amount = parseAmount(amountText)
        quote = if (amount == null) null else runCatching { repository.quote(f.currency, t.currency, amount) }.getOrNull()
    }

    fun exchange() = viewModelScope.launch {
        val f = from ?: return@launch
        val t = to ?: return@launch
        val amount = parseAmount(amountText) ?: return@launch
        busy = true
        error = null
        try {
            val result = repository.exchange(idempotencyKey, ExchangeRequest(f.id, t.id, amount))
            done = formatMoney(result.soldAmount, result.soldCurrency) + " → " +
                formatMoney(result.boughtAmount, result.boughtCurrency)
        } catch (e: Exception) {
            error = e
        } finally {
            busy = false
        }
    }
}

@Composable
fun ExchangeScreen(onBack: () -> Unit, onOpenAccount: () -> Unit) {
    val container = LocalContext.current.container
    val vm: ExchangeViewModel = viewModel { ExchangeViewModel(container.repository) }
    LaunchedEffect(Unit) { vm.load() }
    LaunchedEffect(vm.amountText, vm.from, vm.to) {
        delay(350) // wait until the user pauses typing
        vm.refreshQuote()
    }
    val from = vm.from
    val to = vm.to
    val amount = parseAmount(vm.amountText)

    InnerScreen(stringResource(R.string.exchange_title), onBack) {
        vm.done?.let {
            SuccessPanel(stringResource(R.string.exchange_done), it, onBack)
            return@InnerScreen
        }
        if (!vm.loaded) {
            LoadingBox()
            return@InnerScreen
        }
        if (from == null || to == null) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.need_both_accounts), style = MaterialTheme.typography.bodyLarge)
                PrimaryButton(stringResource(R.string.svc_open_account), onOpenAccount)
            }
            return@InnerScreen
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            LimeCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.you_sell), style = MaterialTheme.typography.labelMedium, color = Lime.Muted)
                LimeTextField(
                    vm.amountText,
                    { vm.amountText = cleanAmountInput(it, currencyDecimals(from.currency)); vm.edited() },
                    label = "",
                    keyboardType = KeyboardType.Decimal,
                    textStyle = MoneyStyle.Large,
                    prefix = from.currency + "  ",
                )
                Text(
                    stringResource(R.string.from_account_balance, from.currency, formatMoney(from.balance, from.currency)),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                val swap = stringResource(R.string.swap)
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Lime.Green)
                        .clickable(role = Role.Button, onClickLabel = swap) { vm.swap() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.ic_swap_vertical), swap, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
            LimeCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.you_get), style = MaterialTheme.typography.labelMedium, color = Lime.Muted)
                Text(
                    vm.quote?.let { formatMoney(it.convertedAmount, to.currency) } ?: formatMoney(java.math.BigDecimal.ZERO, to.currency),
                    style = MoneyStyle.Large,
                    color = Lime.Positive,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                Text(
                    stringResource(R.string.to_account_balance, to.currency, formatMoney(to.balance, to.currency)),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            vm.quote?.let { q ->
                Spacer(Modifier.height(16.dp))
                LimeCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                    SummaryRow(
                        stringResource(R.string.rate_applied),
                        "1 ${q.rateBaseCurrency} = ${formatRate(q.rate)} ${q.rateQuoteCurrency}",
                        MoneyStyle.Small,
                    )
                    RowDivider()
                    SummaryRow(stringResource(R.string.fee), stringResource(R.string.fee_none))
                }
            }
            vm.error?.let { Spacer(Modifier.height(16.dp)); ErrorText(errorMessage(it)) }
        }
        PrimaryButton(
            text = stringResource(R.string.exchange_amount, amount?.let { formatMoney(it, from.currency) } ?: ""),
            onClick = { vm.exchange() },
            enabled = amount != null && vm.quote != null,
            loading = vm.busy,
            modifier = Modifier.padding(20.dp).navigationBarsPadding(),
        )
    }
}

// ------------------------------------------------------------------ statement

class StatementViewModel(private val repository: BankRepository, private val accountId: Long) : ViewModel() {
    var month by mutableStateOf(YearMonth.now()); private set
    var statement by mutableStateOf<Statement?>(null); private set
    var error by mutableStateOf<Throwable?>(null); private set

    fun load() = viewModelScope.launch {
        statement = null
        try {
            statement = repository.statement(accountId, month.toString()); error = null
        } catch (e: Exception) {
            error = e
        }
    }

    fun shift(months: Long) {
        val next = month.plusMonths(months)
        if (next.isAfter(YearMonth.now())) return
        month = next
        load()
    }
}

@Composable
fun StatementScreen(accountId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val vm: StatementViewModel = viewModel(key = "statement-$accountId") {
        StatementViewModel(context.container.repository, accountId)
    }
    LaunchedEffect(accountId) { vm.load() }
    val locale = LocalConfiguration.current.locales[0]

    InnerScreen(stringResource(R.string.statement_title), onBack) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                MonthArrow(R.drawable.ic_back, stringResource(R.string.previous_month)) { vm.shift(-1) }
                Text(
                    vm.month.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale)),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                MonthArrow(R.drawable.ic_chevron, stringResource(R.string.next_month)) { vm.shift(1) }
            }
            vm.error?.let { ErrorText(errorMessage(it)) }
            val s = vm.statement
            if (s == null && vm.error == null) LoadingBox()
            if (s != null) {
                LimeCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                    Text(formatAccountNumber(s.accountNumber) + " · " + s.currency, style = MoneyStyle.Tiny,
                        color = Lime.Muted, modifier = Modifier.padding(vertical = 6.dp))
                    SummaryRow(stringResource(R.string.opening_balance), formatMoney(s.openingBalance, s.currency), MoneyStyle.Small)
                    RowDivider()
                    SummaryRow(stringResource(R.string.money_in), formatSigned(s.totalCredits, s.currency, true),
                        MoneyStyle.Small.copy(color = Lime.Positive))
                    RowDivider()
                    SummaryRow(stringResource(R.string.money_out), formatSigned(s.totalDebits, s.currency, false), MoneyStyle.Small)
                    RowDivider()
                    SummaryRow(stringResource(R.string.closing_balance), formatMoney(s.closingBalance, s.currency),
                        MoneyStyle.Small.copy(fontWeight = FontWeight.Medium))
                }
                if (s.lines.isEmpty()) {
                    Text(stringResource(R.string.no_entries_month), style = MaterialTheme.typography.bodySmall)
                } else {
                    LimeCard(Modifier.fillMaxWidth(), padding = PaddingValues(0.dp)) {
                        s.lines.forEachIndexed { i, line ->
                            if (i > 0) RowDivider()
                            val credit = line.direction == "CREDIT"
                            TransactionRow(
                                title = line.description ?: transactionTypeLabel(line.type),
                                subtitle = shortDate(localDate(line.date), locale) + " · " +
                                    formatMoney(line.balanceAfter, s.currency),
                                amount = formatSigned(line.amount, s.currency, credit),
                                credit = credit,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthArrow(icon: Int, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), description, tint = Lime.Ink, modifier = Modifier.size(20.dp))
    }
}

// ------------------------------------------------------------------ receive (my QR)

/** Draws a QR code into a bitmap; 'H' error correction leaves room for the logo in the middle. */
private fun qrBitmap(content: String, size: Int = 640): Bitmap {
    val matrix = QRCodeWriter().encode(
        content, BarcodeFormat.QR_CODE, size, size,
        mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H, EncodeHintType.MARGIN to 1),
    )
    val pixels = IntArray(size * size) { i -> if (matrix[i % size, i / size]) 0xFF0E1A2B.toInt() else 0xFFFFFFFF.toInt() }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}

@Composable
fun ReceiveScreen(accountId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val container = context.container
    var account by remember { mutableStateOf<Account?>(null) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    LaunchedEffect(accountId) {
        try {
            account = container.repository.account(accountId)
        } catch (e: Exception) {
            error = e
        }
    }

    InnerScreen(stringResource(R.string.receive_title), onBack) {
        Column(
            Modifier.weight(1f).padding(20.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            error?.let { ErrorText(errorMessage(it)) }
            val a = account
            if (a == null && error == null) LoadingBox()
            if (a != null) {
                LimeCard(Modifier.fillMaxWidth(), padding = PaddingValues(24.dp)) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(container.session.fullName.uppercase(), style = MaterialTheme.typography.titleMedium)
                        Text(formatAccountNumber(a.accountNumber) + " · " + a.currency, style = MoneyStyle.Tiny,
                            color = Lime.Muted, modifier = Modifier.padding(top = 4.dp))
                        Spacer(Modifier.height(20.dp))
                        val qr = remember(a.accountNumber) { qrBitmap(a.accountNumber).asImageBitmap() }
                        Box(contentAlignment = Alignment.Center) {
                            Image(qr, contentDescription = formatAccountNumber(a.accountNumber), modifier = Modifier.size(220.dp))
                            Box(
                                Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Lime.Green)
                                    .border(4.dp, Color.White, RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(painterResource(R.drawable.ic_bank), null, tint = Color.White, modifier = Modifier.size(22.dp))
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(stringResource(R.string.receive_hint), style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center)
                    }
                }
                Spacer(Modifier.height(16.dp))
                SecondaryButton(stringResource(R.string.copy_number), { copyAccountNumber(context, a.accountNumber) },
                    Modifier.fillMaxWidth())
            }
        }
        account?.let { a ->
            PrimaryButton(
                stringResource(R.string.share_qr),
                onClick = {
                    val text = "${context.getString(R.string.app_name)} · ${a.currency} · ${formatAccountNumber(a.accountNumber)}"
                    context.startActivity(
                        Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null),
                    )
                },
                modifier = Modifier.padding(20.dp).navigationBarsPadding(),
            )
        }
    }
}
