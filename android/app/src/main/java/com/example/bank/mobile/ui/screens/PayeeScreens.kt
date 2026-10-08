package com.example.bank.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.bank.mobile.R
import com.example.bank.mobile.container
import com.example.bank.mobile.data.Payee
import com.example.bank.mobile.data.Recipient
import com.example.bank.mobile.ui.components.ErrorText
import com.example.bank.mobile.ui.components.Initial
import com.example.bank.mobile.ui.components.LimeCard
import com.example.bank.mobile.ui.components.LimeTextField
import com.example.bank.mobile.ui.components.LoadingBox
import com.example.bank.mobile.ui.components.PrimaryButton
import com.example.bank.mobile.ui.components.RowDivider
import com.example.bank.mobile.ui.formatAccountNumber
import com.example.bank.mobile.ui.theme.Lime
import com.example.bank.mobile.ui.theme.MoneyStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Looks up who owns [accountNumber] once all 11 digits are typed.
 * Returns the recipient, an error, or nothing yet; used by Transfer and Add payee.
 */
@Composable
fun rememberRecipient(accountNumber: String): Pair<Recipient?, Throwable?> {
    val repository = LocalContext.current.container.repository
    var recipient by remember { mutableStateOf<Recipient?>(null) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    LaunchedEffect(accountNumber) {
        recipient = null
        error = null
        if (accountNumber.length == 11) {
            delay(250)
            try {
                recipient = repository.lookup(accountNumber)
            } catch (e: Exception) {
                error = e
            }
        }
    }
    return recipient to error
}

/** "✓ DARA C. · USD" under an account-number field. */
@Composable
fun RecipientLine(accountNumber: String, recipient: Recipient?, error: Throwable?) {
    when {
        recipient != null -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_check), null, tint = Lime.Green, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text("${recipient.holderName} · ${recipient.currency}", color = Lime.Green,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
        }
        error != null -> Text(errorMessage(error), color = Lime.Negative, style = MaterialTheme.typography.labelMedium)
        accountNumber.length == 11 -> Text(stringResource(R.string.recipient_checking), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun PayeesScreen(onPay: (String) -> Unit) {
    val repository = LocalContext.current.container.repository
    val scope = rememberCoroutineScope()
    var payees by remember { mutableStateOf<List<Payee>?>(null) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    var adding by remember { mutableStateOf(false) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(reload) {
        try {
            payees = repository.payees(); error = null
        } catch (e: Exception) {
            error = e
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Lime.Background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.payees_title), style = MaterialTheme.typography.headlineSmall)
        error?.let { ErrorText(errorMessage(it)) }
        val list = payees
        if (list == null && error == null) LoadingBox()
        if (list != null && list.isEmpty()) {
            Text(stringResource(R.string.no_payees), style = MaterialTheme.typography.bodySmall)
        }
        if (!list.isNullOrEmpty()) {
            LimeCard(Modifier.fillMaxWidth(), padding = PaddingValues(0.dp)) {
                list.forEachIndexed { i, payee ->
                    if (i > 0) RowDivider()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button, enabled = payee.available) { onPay(payee.accountNumber) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Initial(payee.nickname, 40, Lime.GreenTint, Lime.GreenDark)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(payee.nickname, style = MaterialTheme.typography.titleSmall)
                            Text(
                                if (payee.available) "${payee.holderName} · ${payee.currency}"
                                else stringResource(R.string.payee_unavailable),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(formatAccountNumber(payee.accountNumber), style = MoneyStyle.Tiny, color = Lime.Muted)
                        }
                        val remove = stringResource(R.string.remove)
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .clickable(role = Role.Button, onClickLabel = remove) {
                                    scope.launch {
                                        runCatching { repository.removePayee(payee.id) }.onFailure { error = it }
                                        reload++
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(painterResource(R.drawable.ic_close), remove, tint = Lime.Muted, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
        PrimaryButton(stringResource(R.string.add_payee), { adding = true })
    }

    if (adding) {
        AddPayeeDialog(onDismiss = { adding = false }, onAdded = { adding = false; reload++ })
    }
}

@Composable
private fun AddPayeeDialog(onDismiss: () -> Unit, onAdded: () -> Unit) {
    val repository = LocalContext.current.container.repository
    val scope = rememberCoroutineScope()
    var number by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<Throwable?>(null) }
    val (recipient, lookupError) = rememberRecipient(number)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_payee)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LimeTextField(number, { number = it.filter(Char::isDigit).take(11) },
                    stringResource(R.string.to_account_number), keyboardType = KeyboardType.Number,
                    textStyle = MoneyStyle.Medium)
                RecipientLine(number, recipient, lookupError)
                LimeTextField(nickname, { nickname = it.take(50) }, stringResource(R.string.nickname))
                error?.let { ErrorText(errorMessage(it)) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = recipient != null && nickname.isNotBlank(),
                onClick = {
                    scope.launch {
                        try {
                            repository.addPayee(number, nickname)
                            onAdded()
                        } catch (e: Exception) {
                            error = e
                        }
                    }
                },
            ) { Text(stringResource(R.string.save), color = Lime.Green, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = Lime.Muted) } },
        containerColor = Lime.Card,
    )
}
