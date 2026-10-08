package com.example.bank.mobile.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.bank.mobile.R
import com.example.bank.mobile.container
import com.example.bank.mobile.ui.components.Initial
import com.example.bank.mobile.ui.components.InnerScreen
import com.example.bank.mobile.ui.components.LimeCard
import com.example.bank.mobile.ui.components.RowDivider
import com.example.bank.mobile.ui.formatMoney
import com.example.bank.mobile.ui.theme.Lime
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    onChangePin: () -> Unit,
    onTwoStep: () -> Unit,
    onChangePassword: () -> Unit,
    onSignedOut: () -> Unit,
) {
    val context = LocalContext.current
    val session = context.container.session
    val scope = rememberCoroutineScope()
    val language = LocalConfiguration.current.locales[0].language
    var server by remember { mutableStateOf("") }
    var twoStepOn by remember { mutableStateOf<Boolean?>(null) }
    var limitText by remember { mutableStateOf<String?>(null) }
    val limitFormat = stringResource(R.string.limit_value)
    LaunchedEffect(Unit) {
        server = session.currentServer()
        val repository = context.container.repository
        twoStepOn = runCatching { repository.me().twoStepEnabled }.getOrNull()
        limitText = runCatching {
            val account = repository.accounts().firstOrNull { it.currency == "USD" && it.status == "ACTIVE" }
                ?: return@runCatching null
            val limit = repository.limits(account.id)
            val max = limit.dailyLimit ?: return@runCatching null
            String.format(limitFormat, formatMoney(limit.remainingToday ?: max, limit.currency), formatMoney(max, limit.currency))
        }.getOrNull()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Lime.Background)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Lime.GreenDark)
                .statusBarsPadding()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Initial(session.fullName, 56, Color.White, Lime.GreenDark)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(session.fullName, color = Color.White, style = MaterialTheme.typography.titleLarge)
                Text(session.email, color = Lime.GreenOnDark, style = MaterialTheme.typography.bodySmall)
            }
        }

        SectionTitle(stringResource(R.string.security))
        LimeCard(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), padding = PaddingValues(0.dp)) {
            SettingRow(R.drawable.ic_lock, stringResource(R.string.change_pin), onClick = onChangePin)
            RowDivider()
            SettingRow(R.drawable.ic_lock, stringResource(R.string.change_password), onClick = onChangePassword)
            RowDivider()
            SettingRow(
                R.drawable.ic_check, stringResource(R.string.two_step),
                trailing = twoStepOn?.let { stringResource(if (it) R.string.on else R.string.off) },
                onClick = onTwoStep,
            )
            RowDivider()
            SettingRow(R.drawable.ic_user, stringResource(R.string.fingerprint), trailing = stringResource(R.string.coming_soon))
            RowDivider()
            SettingRow(R.drawable.ic_transfer, stringResource(R.string.daily_limit), trailing = limitText ?: "—")
        }

        SectionTitle(stringResource(R.string.app_section))
        LimeCard(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), padding = PaddingValues(0.dp)) {
            SettingRow(R.drawable.ic_globe, stringResource(R.string.language), trailing = stringResource(R.string.language_value),
                onClick = { toggleLanguage(language) })
            RowDivider()
            SettingRow(R.drawable.ic_server, stringResource(R.string.server_address), trailing = server)
        }

        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.log_out),
            color = Lime.Negative,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
                .border(1.dp, Color(0xFFE3B7B2), RoundedCornerShape(12.dp))
                .clickable(role = Role.Button) { scope.launch { session.signOut(); onSignedOut() } }
                .padding(16.dp),
        )
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = Lime.Muted,
        modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp))
}

@Composable
private fun SettingRow(
    @DrawableRes icon: Int,
    title: String,
    trailing: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), null, tint = Lime.Green, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        if (trailing != null) {
            Text(trailing, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
        if (onClick != null) {
            Spacer(Modifier.width(6.dp))
            Icon(painterResource(R.drawable.ic_chevron), null, tint = Lime.Muted, modifier = Modifier.size(16.dp))
        }
    }
}

/** For features whose back end isn't there yet (QR payments, payees, notifications). */
@Composable
fun ComingSoonScreen(feature: String, onBack: () -> Unit) {
    InnerScreen(feature, onBack) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.size(84.dp).clip(CircleShape).background(Lime.GreenTint), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_bank), null, tint = Lime.Green, modifier = Modifier.size(40.dp))
            }
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.coming_soon), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.coming_soon_body, feature), style = MaterialTheme.typography.bodyLarge,
                color = Lime.Muted, textAlign = TextAlign.Center)
        }
    }
}
