package com.example.bank.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bank.mobile.R
import com.example.bank.mobile.container
import com.example.bank.mobile.data.ApiException
import com.example.bank.mobile.data.NetworkException
import com.example.bank.mobile.data.SessionManager
import com.example.bank.mobile.ui.components.ErrorText
import com.example.bank.mobile.ui.components.LimeTextField
import com.example.bank.mobile.ui.components.PrimaryButton
import com.example.bank.mobile.ui.theme.Lime
import kotlinx.coroutines.launch

/** Turns any failure into a sentence for the user. */
@Composable
fun errorMessage(e: Throwable): String = when (e) {
    is NetworkException -> stringResource(R.string.error_network)
    is ApiException -> e.message
    else -> stringResource(R.string.error_generic)
}

@Composable
private fun BankMark(size: Int = 64) {
    Box(
        Modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size * 0.28).dp))
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_bank), null, tint = Lime.Green, modifier = Modifier.size((size * 0.53).dp))
    }
}

/** Email + password sign-in, with a switch to registration. */
@Composable
fun SignInScreen(onSignedIn: (emailVerified: Boolean) -> Unit, onForgotPassword: () -> Unit) {
    val session = LocalContext.current.container.session
    val scope = rememberCoroutineScope()
    var registering by remember { mutableStateOf(false) }
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var server by remember { mutableStateOf("") }
    var showServer by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    var needsTotp by remember { mutableStateOf(false) }
    var totp by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { server = session.currentServer() }

    Column(
        Modifier
            .fillMaxSize()
            .background(Lime.Background)
            .verticalScroll(rememberScrollState())
            .imePadding(),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Lime.GreenDark)
                .statusBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 40.dp, bottom = 32.dp),
        ) {
            BankMark()
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.login_subtitle), color = Lime.GreenOnDark, style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(if (registering) R.string.register_title else R.string.login_title),
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 28.sp),
            )
        }

        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (registering) {
                LimeTextField(fullName, { fullName = it }, stringResource(R.string.full_name))
            }
            LimeTextField(email, { email = it }, stringResource(R.string.email), keyboardType = KeyboardType.Email)
            LimeTextField(
                password, { password = it }, stringResource(R.string.password),
                keyboardType = KeyboardType.Password,
                visualTransformation = PasswordVisualTransformation(),
                supportingText = if (registering) stringResource(R.string.password_rule) else null,
            )
            if (needsTotp) {
                LimeTextField(
                    totp, { totp = it.filter(Char::isDigit).take(6) }, stringResource(R.string.code),
                    keyboardType = KeyboardType.NumberPassword,
                    supportingText = stringResource(R.string.two_step_prompt),
                )
            }
            if (showServer) {
                LimeTextField(server, { server = it.trim() }, stringResource(R.string.server_address), keyboardType = KeyboardType.Uri)
            }

            error?.let { ErrorText(errorMessage(it)) }

            PrimaryButton(
                text = stringResource(if (registering) R.string.create_account else R.string.sign_in),
                loading = busy,
                enabled = email.isNotBlank() && password.isNotBlank() && (!registering || fullName.isNotBlank()),
                onClick = {
                    scope.launch {
                        busy = true
                        error = null
                        try {
                            if (registering) session.register(server, fullName, email, password)
                            else session.signIn(server, email, password, totp.takeIf { needsTotp && it.length == 6 })
                            onSignedIn(session.emailVerified)
                        } catch (e: ApiException) {
                            if (e.code == "TOTP_REQUIRED") needsTotp = true else error = e
                        } catch (e: Exception) {
                            error = e
                        } finally {
                            busy = false
                        }
                    }
                },
            )

            Text(
                stringResource(if (registering) R.string.have_account else R.string.no_account_yet),
                color = Lime.Green,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { registering = !registering; error = null }
                    .padding(12.dp),
            )
            if (!registering) {
                Text(
                    stringResource(R.string.forgot_password),
                    color = Lime.Muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button, onClick = onForgotPassword)
                        .padding(8.dp),
                )
            }
            Text(
                stringResource(R.string.change_server),
                color = Lime.Muted,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { showServer = !showServer }
                    .padding(8.dp),
            )
        }
    }
}

/** 6 dots that fill as digits are typed. */
@Composable
private fun PinDots(filled: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        repeat(PIN_LENGTH) { i ->
            Box(
                Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(if (i < filled) Color.White else Color.White.copy(alpha = 0.25f)),
            )
        }
    }
}

@Composable
private fun PinPad(onDigit: (Char) -> Unit, onDelete: () -> Unit) {
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "⌫")
    Column(Modifier.padding(horizontal = 34.dp)) {
        keys.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { key ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(64.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .then(
                                when (key) {
                                    "" -> Modifier
                                    "⌫" -> Modifier.clickable(role = Role.Button, onClickLabel = "delete", onClick = onDelete)
                                    else -> Modifier.clickable(role = Role.Button) { onDigit(key[0]) }
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        when (key) {
                            "⌫" -> Icon(painterResource(R.drawable.ic_backspace), stringResource(R.string.delete_digit),
                                tint = Color.White, modifier = Modifier.size(26.dp))
                            "" -> Unit
                            else -> Text(key, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

private const val PIN_LENGTH = 6

/** Green full-screen PIN entry used for both creating and entering the PIN. */
@Composable
private fun PinLayout(
    title: String,
    subtitle: String?,
    message: String?,
    entered: Int,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    footer: @Composable () -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Lime.GreenDark)
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(56.dp))
        BankMark()
        Spacer(Modifier.height(22.dp))
        if (subtitle != null) Text(subtitle, color = Lime.GreenOnDark, style = MaterialTheme.typography.bodyMedium)
        Text(title, color = Color.White, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(22.dp))
        PinDots(entered)
        Spacer(Modifier.height(16.dp))
        Text(message ?: " ", color = Color(0xFFFFD7D2), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.weight(1f))
        PinPad(onDigit, onDelete)
        footer()
        Spacer(Modifier.height(16.dp))
    }
}

/** Choose a PIN, then type it again to confirm. */
@Composable
fun CreatePinScreen(onDone: () -> Unit) {
    val session = LocalContext.current.container.session
    val scope = rememberCoroutineScope()
    var first by remember { mutableStateOf<String?>(null) }
    var pin by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    val mismatch = stringResource(R.string.pin_mismatch)

    PinLayout(
        title = stringResource(if (first == null) R.string.pin_create else R.string.pin_confirm),
        subtitle = if (first == null) stringResource(R.string.pin_create_hint) else null,
        message = message,
        entered = pin.length,
        onDigit = { d ->
            if (pin.length >= PIN_LENGTH) return@PinLayout
            pin += d
            message = null
            if (pin.length == PIN_LENGTH) {
                val chosen = first
                when {
                    chosen == null -> { first = pin; pin = "" }
                    chosen == pin -> scope.launch { session.createPin(pin); onDone() }
                    else -> { first = null; pin = ""; message = mismatch }
                }
            }
        },
        onDelete = { pin = pin.dropLast(1) },
    )
}

/** Daily entry point: unlock with the PIN. */
@Composable
fun UnlockScreen(onUnlocked: () -> Unit, onSignInAgain: () -> Unit) {
    val context = LocalContext.current
    val session = context.container.session
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val networkError = stringResource(R.string.error_network)
    val genericError = stringResource(R.string.error_generic)

    PinLayout(
        title = session.fullName.ifBlank { stringResource(R.string.pin_enter) },
        subtitle = stringResource(R.string.welcome_back),
        message = message,
        entered = pin.length,
        onDigit = { d ->
            if (busy || pin.length >= PIN_LENGTH) return@PinLayout
            pin += d
            message = null
            if (pin.length == PIN_LENGTH) {
                busy = true
                scope.launch {
                    try {
                        when (val result = session.unlock(pin)) {
                            SessionManager.UnlockResult.Ok -> onUnlocked()
                            SessionManager.UnlockResult.LockedOut -> onSignInAgain()
                            is SessionManager.UnlockResult.WrongPin ->
                                message = context.getString(R.string.pin_wrong, result.triesLeft)
                        }
                    } catch (e: NetworkException) {
                        message = networkError
                    } catch (e: Exception) {
                        // Refresh token expired: the session was cleared, sign in again.
                        message = genericError
                        onSignInAgain()
                    } finally {
                        pin = ""
                        busy = false
                    }
                }
            }
        },
        onDelete = { pin = pin.dropLast(1) },
        footer = {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.forgot_pin),
                    color = Lime.GreenOnDark,
                    modifier = Modifier
                        .clickable(role = Role.Button) { scope.launch { session.signOut(); onSignInAgain() } }
                        .padding(8.dp),
                )
                if (session.fullName.isNotBlank()) {
                    Text("·", color = Lime.GreenOnDark)
                    Text(
                        stringResource(R.string.not_you, session.fullName.substringBefore(' ')),
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clickable(role = Role.Button) { scope.launch { session.signOut(); onSignInAgain() } }
                            .padding(8.dp),
                    )
                }
            }
        },
    )
}
