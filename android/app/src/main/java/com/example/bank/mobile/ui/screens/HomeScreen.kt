package com.example.bank.mobile.ui.screens

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bank.mobile.R
import com.example.bank.mobile.container
import com.example.bank.mobile.data.Account
import com.example.bank.mobile.data.AccountTransaction
import com.example.bank.mobile.data.BankRepository
import com.example.bank.mobile.data.ExchangeRate
import com.example.bank.mobile.ui.components.ErrorText
import com.example.bank.mobile.ui.components.Initial
import com.example.bank.mobile.ui.components.LimeCard
import com.example.bank.mobile.ui.components.RowDivider
import com.example.bank.mobile.ui.components.ServiceTile
import com.example.bank.mobile.ui.components.TransactionRow
import com.example.bank.mobile.ui.formatAccountNumber
import com.example.bank.mobile.ui.formatMoney
import com.example.bank.mobile.ui.formatRate
import com.example.bank.mobile.ui.formatSigned
import com.example.bank.mobile.ui.localDate
import com.example.bank.mobile.ui.shortDate
import com.example.bank.mobile.ui.theme.Lime
import com.example.bank.mobile.ui.theme.MoneyStyle
import kotlinx.coroutines.launch
import java.time.LocalTime

class HomeViewModel(private val repository: BankRepository) : ViewModel() {
    var accounts by mutableStateOf<List<Account>>(emptyList()); private set
    var rate by mutableStateOf<ExchangeRate?>(null); private set
    var currency by mutableStateOf("USD"); private set
    var recent by mutableStateOf<List<AccountTransaction>>(emptyList()); private set
    var error by mutableStateOf<Throwable?>(null); private set
    var balanceHidden by mutableStateOf(false)

    val selected: Account? get() = accounts.firstOrNull { it.currency == currency && it.status != "CLOSED" }

    fun load() = viewModelScope.launch {
        try {
            accounts = repository.accounts()
            error = null
            if (selected == null) accounts.firstOrNull { it.status != "CLOSED" }?.let { currency = it.currency }
            rate = runCatching { repository.rates() }.getOrNull()
                ?.firstOrNull { it.baseCurrency == "USD" && it.quoteCurrency == "KHR" }
            loadRecent()
        } catch (e: Exception) {
            error = e
        }
    }

    fun select(newCurrency: String) {
        currency = newCurrency
        viewModelScope.launch { loadRecent() }
    }

    private suspend fun loadRecent() {
        recent = selected?.let { runCatching { repository.history(it.id, 5) }.getOrNull() } ?: emptyList()
    }
}

/** Pulls a composable up by [amount] and shrinks its layout height to match (cards overlapping the header). */
private fun Modifier.pullUp(amount: Dp) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val shift = amount.roundToPx()
    layout(placeable.width, (placeable.height - shift).coerceAtLeast(0)) { placeable.place(0, -shift) }
}

fun toggleLanguage(currentLanguage: String) {
    val next = if (currentLanguage == "km") "en" else "km"
    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(next))
}

@Composable
fun transactionTypeLabel(type: String): String = stringResource(
    when (type) {
        "DEPOSIT" -> R.string.type_DEPOSIT
        "WITHDRAWAL" -> R.string.type_WITHDRAWAL
        "EXCHANGE" -> R.string.type_EXCHANGE
        else -> R.string.type_TRANSFER
    },
)

@Composable
fun HomeScreen(
    onTransfer: () -> Unit,
    onExchange: () -> Unit,
    onMyQr: (Long) -> Unit,
    onAccounts: () -> Unit,
    onAccount: (Long) -> Unit,
    onStatements: (Long) -> Unit,
    onOpenAccount: () -> Unit,
    onComingSoon: (String) -> Unit,
    onProfile: () -> Unit,
) {
    val context = LocalContext.current
    val container = context.container
    val vm: HomeViewModel = viewModel { HomeViewModel(container.repository) }
    LaunchedEffect(Unit) { vm.load() }
    val language = LocalConfiguration.current.locales[0].language
    val name = container.session.fullName

    Column(
        Modifier
            .fillMaxSize()
            .background(Lime.Background)
            .verticalScroll(rememberScrollState()),
    ) {
        // Header band
        Row(
            Modifier
                .fillMaxWidth()
                .background(Lime.GreenDark)
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 76.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.clickable(role = Role.Button, onClick = onProfile)) {
                Initial(name, 42, Color.White, Lime.GreenDark)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(greetingRes()), color = Lime.GreenOnDark, style = MaterialTheme.typography.bodySmall)
                Text(name.substringBefore(' '), color = Color.White, style = MaterialTheme.typography.titleMedium)
            }
            Text(
                stringResource(R.string.language_switch),
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .border(1.dp, Lime.GreenOnDark.copy(alpha = 0.6f), RoundedCornerShape(50))
                    .clickable(role = Role.Button) { toggleLanguage(language) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
            Spacer(Modifier.width(8.dp))
            val notifications = stringResource(R.string.notifications)
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f))
                    .clickable(role = Role.Button, onClickLabel = notifications) { onComingSoon(notifications) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_bell), notifications, tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }

        BalanceCard(vm, onAccount, onOpenAccount, Modifier.padding(horizontal = 20.dp).pullUp(56.dp))

        vm.error?.let { ErrorText(errorMessage(it), Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) }

        // Services
        val selectedId = vm.selected?.id
        val payees = stringResource(R.string.svc_payees)
        val services = listOf(
            Triple(R.drawable.ic_transfer, R.string.svc_transfer) { onTransfer() },
            Triple(R.drawable.ic_qr, R.string.svc_my_qr) { selectedId?.let(onMyQr) ?: onOpenAccount() },
            Triple(R.drawable.ic_exchange, R.string.svc_exchange) { onExchange() },
            Triple(R.drawable.ic_people, R.string.svc_payees) { onComingSoon(payees) },
            Triple(R.drawable.ic_card, R.string.svc_accounts) { onAccounts() },
            Triple(R.drawable.ic_statement, R.string.svc_statements) { selectedId?.let(onStatements) ?: onOpenAccount() },
            Triple(R.drawable.ic_plus, R.string.svc_open_account) { onOpenAccount() },
            Triple(R.drawable.ic_lock, R.string.svc_security) { onProfile() },
        )
        Column(Modifier.padding(horizontal = 12.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            services.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { (icon, label, action) ->
                        ServiceTile(icon, stringResource(label), action, Modifier.weight(1f))
                    }
                }
            }
        }

        // Recent activity
        LimeCard(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 24.dp).fillMaxWidth(),
            padding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.recent_activity), style = MaterialTheme.typography.titleMedium)
                vm.selected?.let { account ->
                    Text(
                        stringResource(R.string.view_all),
                        color = Lime.Green,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.clickable(role = Role.Button) { onAccount(account.id) }.padding(4.dp),
                    )
                }
            }
            if (vm.recent.isEmpty()) {
                RowDivider()
                Text(stringResource(R.string.no_activity), style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(16.dp))
            }
            val locale = LocalConfiguration.current.locales[0]
            vm.recent.forEach { tx ->
                RowDivider()
                TransactionRow(
                    title = tx.description ?: transactionTypeLabel(tx.type),
                    subtitle = transactionTypeLabel(tx.type) + " · " + shortDate(localDate(tx.createdAt), locale),
                    amount = formatSigned(tx.amount, tx.currency, tx.isCredit),
                    credit = tx.isCredit,
                )
            }
        }
    }
}

private fun greetingRes(): Int = when (LocalTime.now().hour) {
    in 5..11 -> R.string.greeting_morning
    in 12..17 -> R.string.greeting_afternoon
    else -> R.string.greeting_evening
}

@Composable
private fun BalanceCard(
    vm: HomeViewModel,
    onAccount: (Long) -> Unit,
    onOpenAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val account = vm.selected
    LimeCard(modifier.fillMaxWidth().shadow(8.dp, RoundedCornerShape(16.dp), clip = false)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // USD / KHR switch
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFFF1F3F6))
                    .padding(3.dp),
            ) {
                listOf("USD", "KHR").forEach { c ->
                    val active = c == vm.currency
                    Text(
                        c,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = if (active) Lime.Ink else Lime.Muted,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (active) Color.White else Color.Transparent)
                            .clickable(role = Role.Tab) { vm.select(c) }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            account?.let { Text(formatAccountNumber(it.accountNumber), style = MoneyStyle.Tiny, color = Lime.Muted) }
        }

        if (account == null) {
            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.no_account_title, vm.currency), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.open_one),
                color = Lime.Green,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(role = Role.Button, onClick = onOpenAccount).padding(vertical = 8.dp),
            )
        } else {
            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.available_balance), style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (vm.balanceHidden) "••••••" else formatMoney(account.balance, account.currency),
                    style = MoneyStyle.Large,
                    modifier = Modifier.clickable(role = Role.Button) { onAccount(account.id) },
                )
                Spacer(Modifier.width(6.dp))
                val toggle = stringResource(if (vm.balanceHidden) R.string.show_balance else R.string.hide_balance)
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClickLabel = toggle) { vm.balanceHidden = !vm.balanceHidden },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(if (vm.balanceHidden) R.drawable.ic_eye_off else R.drawable.ic_eye),
                        toggle, tint = Lime.Muted, modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        vm.rate?.let { rate ->
            RowDivider()
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.rate_today), style = MaterialTheme.typography.bodySmall)
                Text(
                    stringResource(R.string.rate_buy_sell, formatRate(rate.buyRate), formatRate(rate.sellRate)),
                    style = MoneyStyle.Tiny.copy(color = Lime.Ink),
                )
            }
        }
    }
}
