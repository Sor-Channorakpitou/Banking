package com.example.bank.mobile.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bank.mobile.R
import com.example.bank.mobile.container
import com.example.bank.mobile.data.Account
import com.example.bank.mobile.data.AccountTransaction
import com.example.bank.mobile.data.BankRepository
import com.example.bank.mobile.ui.components.ErrorText
import com.example.bank.mobile.ui.components.InnerScreen
import com.example.bank.mobile.ui.components.LimeCard
import com.example.bank.mobile.ui.components.LoadingBox
import com.example.bank.mobile.ui.components.PrimaryButton
import com.example.bank.mobile.ui.components.RowDivider
import com.example.bank.mobile.ui.components.TransactionRow
import com.example.bank.mobile.ui.formatAccountNumber
import com.example.bank.mobile.ui.formatMoney
import com.example.bank.mobile.ui.formatSigned
import com.example.bank.mobile.ui.localDate
import com.example.bank.mobile.ui.shortDate
import com.example.bank.mobile.ui.theme.Lime
import com.example.bank.mobile.ui.theme.MoneyStyle
import com.example.bank.mobile.ui.time
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun statusLabel(status: String): String = stringResource(
    when (status) {
        "FROZEN" -> R.string.status_FROZEN
        "CLOSED" -> R.string.status_CLOSED
        else -> R.string.status_ACTIVE
    },
)

fun copyAccountNumber(context: Context, number: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText("Account number", number))
    Toast.makeText(context, context.getString(R.string.copied), Toast.LENGTH_SHORT).show()
}

// ------------------------------------------------------------------ list (bottom tab)

class AccountsViewModel(private val repository: BankRepository) : ViewModel() {
    var accounts by mutableStateOf<List<Account>?>(null); private set
    var error by mutableStateOf<Throwable?>(null); private set

    fun load() = viewModelScope.launch {
        try {
            accounts = repository.accounts(); error = null
        } catch (e: Exception) {
            error = e
        }
    }
}

@Composable
fun AccountsScreen(onAccount: (Long) -> Unit, onOpenAccount: () -> Unit) {
    val container = LocalContext.current.container
    val vm: AccountsViewModel = viewModel { AccountsViewModel(container.repository) }
    LaunchedEffect(Unit) { vm.load() }

    Column(
        Modifier
            .fillMaxSize()
            .background(Lime.Background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.accounts_title), style = MaterialTheme.typography.headlineSmall)
        vm.error?.let { ErrorText(errorMessage(it)) }
        val accounts = vm.accounts
        if (accounts == null && vm.error == null) LoadingBox()
        accounts?.forEach { account ->
            LimeCard(Modifier.fillMaxWidth().clickable(role = Role.Button) { onAccount(account.id) }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.account_label, account.currency), style = MaterialTheme.typography.bodySmall)
                    Text(formatAccountNumber(account.accountNumber), style = MoneyStyle.Tiny, color = Lime.Muted)
                }
                Spacer(Modifier.height(8.dp))
                Text(formatMoney(account.balance, account.currency), style = MoneyStyle.Large)
                Spacer(Modifier.height(4.dp))
                Text(
                    statusLabel(account.status),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = if (account.status == "ACTIVE") Lime.Green else Lime.Negative,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        PrimaryButton(stringResource(R.string.svc_open_account), onOpenAccount)
    }
}

// ------------------------------------------------------------------ detail + history

class AccountDetailViewModel(private val repository: BankRepository, private val accountId: Long) : ViewModel() {
    var account by mutableStateOf<Account?>(null); private set
    var history by mutableStateOf<List<AccountTransaction>>(emptyList()); private set
    var error by mutableStateOf<Throwable?>(null); private set
    var filter by mutableStateOf(Filter.ALL)

    enum class Filter { ALL, IN, OUT }

    val visible: List<AccountTransaction>
        get() = when (filter) {
            Filter.ALL -> history
            Filter.IN -> history.filter { it.isCredit }
            Filter.OUT -> history.filter { !it.isCredit }
        }

    fun load() = viewModelScope.launch {
        try {
            account = repository.account(accountId)
            history = repository.history(accountId, 100)
            error = null
        } catch (e: Exception) {
            error = e
        }
    }
}

@Composable
fun AccountDetailScreen(
    accountId: Long,
    onBack: () -> Unit,
    onStatement: (Long) -> Unit,
    onShareQr: (Long) -> Unit,
) {
    val context = LocalContext.current
    val vm: AccountDetailViewModel = viewModel(key = "account-$accountId") {
        AccountDetailViewModel(context.container.repository, accountId)
    }
    LaunchedEffect(accountId) { vm.load() }
    val account = vm.account
    val locale = LocalConfiguration.current.locales[0]
    val today = LocalDate.now()

    InnerScreen(title = account?.let { stringResource(R.string.account_label, it.currency) } ?: "", onBack = onBack) {
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
            modifier = Modifier.navigationBarsPadding(),
        ) {
            item {
                vm.error?.let { ErrorText(errorMessage(it)) }
                if (account == null && vm.error == null) LoadingBox()
                account?.let { a ->
                    LimeCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(R.string.available_balance), style = MaterialTheme.typography.bodySmall)
                            Text(formatAccountNumber(a.accountNumber), style = MoneyStyle.Tiny, color = Lime.Muted)
                        }
                        Text(formatMoney(a.balance, a.currency), style = MoneyStyle.Large, modifier = Modifier.padding(top = 6.dp))
                        Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ActionChip(stringResource(R.string.copy_number)) { copyAccountNumber(context, a.accountNumber) }
                            ActionChip(stringResource(R.string.statement)) { onStatement(a.id) }
                            ActionChip(stringResource(R.string.share_qr)) { onShareQr(a.id) }
                        }
                    }
                }
                Row(Modifier.padding(top = 18.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterPill(stringResource(R.string.filter_all), vm.filter == AccountDetailViewModel.Filter.ALL) {
                        vm.filter = AccountDetailViewModel.Filter.ALL
                    }
                    FilterPill(stringResource(R.string.filter_in), vm.filter == AccountDetailViewModel.Filter.IN) {
                        vm.filter = AccountDetailViewModel.Filter.IN
                    }
                    FilterPill(stringResource(R.string.filter_out), vm.filter == AccountDetailViewModel.Filter.OUT) {
                        vm.filter = AccountDetailViewModel.Filter.OUT
                    }
                }
                if (account != null && vm.visible.isEmpty()) {
                    Text(stringResource(R.string.no_activity), style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 16.dp))
                }
            }

            // One card per day, newest day first.
            val days = vm.visible.groupBy { localDate(it.createdAt) }
            days.forEach { (day, txs) ->
                item(key = day.toString()) {
                    val label = when (day) {
                        today -> stringResource(R.string.today)
                        today.minusDays(1) -> stringResource(R.string.yesterday)
                        else -> shortDate(day, locale)
                    }
                    Text(label, style = MaterialTheme.typography.labelMedium, color = Lime.Muted,
                        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
                    LimeCard(Modifier.fillMaxWidth(), padding = PaddingValues(0.dp)) {
                        txs.forEachIndexed { i, tx ->
                            if (i > 0) RowDivider()
                            TransactionRow(
                                title = tx.description ?: transactionTypeLabel(tx.type),
                                subtitle = transactionTypeLabel(tx.type) + " · " + time(tx.createdAt),
                                amount = formatSigned(tx.amount, tx.currency, tx.isCredit),
                                credit = tx.isCredit,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionChip(text: String, onClick: () -> Unit) {
    Text(
        text,
        color = Lime.Green,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Lime.GreenTint)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun FilterPill(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text,
        color = if (selected) Color.White else Lime.Ink,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Lime.Ink else Lime.Card)
            .border(1.dp, if (selected) Lime.Ink else Lime.Border, RoundedCornerShape(50))
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

// ------------------------------------------------------------------ open account

@Composable
fun OpenAccountScreen(onBack: () -> Unit, onOpened: (Long) -> Unit) {
    val context = LocalContext.current
    val repository = context.container.repository
    val scope = rememberCoroutineScope()
    var currency by remember { mutableStateOf("USD") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    val options = listOf("USD" to R.string.currency_USD, "KHR" to R.string.currency_KHR, "EUR" to R.string.currency_EUR)

    InnerScreen(stringResource(R.string.open_account_title), onBack) {
        Column(Modifier.padding(20.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.choose_currency), style = MaterialTheme.typography.labelMedium, color = Lime.Muted)
            options.forEach { (code, label) ->
                val selected = currency == code
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Lime.Card)
                        .border(if (selected) 2.dp else 1.dp, if (selected) Lime.Green else Lime.Border, RoundedCornerShape(16.dp))
                        .clickable(role = Role.RadioButton) { currency = code }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(code, style = MoneyStyle.Medium, modifier = Modifier.padding(end = 14.dp))
                    Text(stringResource(label), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                }
            }
            error?.let { ErrorText(errorMessage(it)) }
        }
        PrimaryButton(
            stringResource(R.string.open_account_button),
            loading = busy,
            modifier = Modifier.padding(20.dp).navigationBarsPadding(),
            onClick = {
                scope.launch {
                    busy = true
                    error = null
                    try {
                        val account = repository.openAccount(currency)
                        Toast.makeText(context, context.getString(R.string.account_opened, currency), Toast.LENGTH_SHORT).show()
                        onOpened(account.id)
                    } catch (e: Exception) {
                        error = e
                    } finally {
                        busy = false
                    }
                }
            },
        )
    }
}
