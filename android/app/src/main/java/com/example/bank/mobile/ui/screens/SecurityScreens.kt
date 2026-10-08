package com.example.bank.mobile.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.bank.mobile.R
import com.example.bank.mobile.container
import com.example.bank.mobile.data.ApiException
import com.example.bank.mobile.data.TwoStepSetup
import com.example.bank.mobile.ui.components.ErrorText
import com.example.bank.mobile.ui.components.InnerScreen
import com.example.bank.mobile.ui.components.LimeCard
import com.example.bank.mobile.ui.components.LimeTextField
import com.example.bank.mobile.ui.components.LoadingBox
import com.example.bank.mobile.ui.components.PrimaryButton
import com.example.bank.mobile.ui.components.SecondaryButton
import com.example.bank.mobile.ui.qrBitmap
import com.example.bank.mobile.ui.theme.Lime
import com.example.bank.mobile.ui.theme.MoneyStyle
import kotlinx.coroutines.launch

/** Six digits only. */
private fun digits6(input: String) = input.filter(Char::isDigit).take(6)

/** After registration (and from the Home reminder): type the emailed code. */
@Composable
fun VerifyEmailScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val container = context.container
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Throwable?>(null) }

    InnerScreen(stringResource(R.string.verify_title), onBack = onDone) {
        Column(Modifier.weight(1f).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.verify_body, container.session.email), style = MaterialTheme.typography.bodyLarge)
            LimeTextField(code, { code = digits6(it) }, stringResource(R.string.code),
                keyboardType = KeyboardType.NumberPassword, textStyle = MoneyStyle.Large)
            error?.let { ErrorText(errorMessage(it)) }
            Text(
                stringResource(R.string.resend_code),
                color = Lime.Green,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clickable(role = Role.Button) {
                        scope.launch {
                            runCatching { container.repository.resendVerification() }
                                .onSuccess {
                                    Toast.makeText(context, context.getString(R.string.code_sent), Toast.LENGTH_SHORT).show()
                                }
                                .onFailure { error = it }
                        }
                    }
                    .padding(vertical = 8.dp),
            )
        }
        Column(Modifier.padding(20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton(
                stringResource(R.string.verify_button),
                enabled = code.length == 6,
                loading = busy,
                onClick = {
                    scope.launch {
                        busy = true
                        error = null
                        try {
                            container.session.verifyEmail(code)
                            onDone()
                        } catch (e: Exception) {
                            error = e
                        } finally {
                            busy = false
                        }
                    }
                },
            )
            SecondaryButton(stringResource(R.string.later), onDone, Modifier.fillMaxWidth())
        }
    }
}

/** Ask for a code by email, then choose a new password with it. */
@Composable
fun ForgotPasswordScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val session = context.container.session
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var codeSent by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Throwable?>(null) }

    fun run(action: suspend () -> Unit) = scope.launch {
        busy = true
        error = null
        try {
            action()
        } catch (e: Exception) {
            error = e
        } finally {
            busy = false
        }
    }

    InnerScreen(stringResource(R.string.reset_title), onBack) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LimeTextField(email, { email = it.trim() }, stringResource(R.string.email), keyboardType = KeyboardType.Email)
            if (codeSent) {
                Text(stringResource(R.string.reset_sent, email), style = MaterialTheme.typography.bodyMedium, color = Lime.Muted)
                LimeTextField(code, { code = digits6(it) }, stringResource(R.string.code),
                    keyboardType = KeyboardType.NumberPassword, textStyle = MoneyStyle.Medium)
                LimeTextField(password, { password = it }, stringResource(R.string.new_password),
                    keyboardType = KeyboardType.Password, visualTransformation = PasswordVisualTransformation(),
                    supportingText = stringResource(R.string.password_rule))
            }
            error?.let { ErrorText(errorMessage(it)) }
        }
        if (!codeSent) {
            PrimaryButton(stringResource(R.string.send_code), enabled = email.contains('@'), loading = busy,
                modifier = Modifier.padding(20.dp).navigationBarsPadding(),
                onClick = { run { session.forgotPassword(session.currentServer(), email); codeSent = true } })
        } else {
            PrimaryButton(stringResource(R.string.reset_button), enabled = code.length == 6 && password.length >= 8,
                loading = busy, modifier = Modifier.padding(20.dp).navigationBarsPadding(),
                onClick = {
                    run {
                        session.resetPassword(email, code, password)
                        Toast.makeText(context, context.getString(R.string.reset_done), Toast.LENGTH_LONG).show()
                        onBack()
                    }
                })
        }
    }
}

/** Turn two-step login on (scan key, confirm with a code) or off (confirm with a code). */
@Composable
fun TwoStepScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = context.container.repository
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf<Boolean?>(null) }
    var setup by remember { mutableStateOf<TwoStepSetup?>(null) }
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Throwable?>(null) }

    LaunchedEffect(Unit) {
        try {
            val on = repository.me().twoStepEnabled
            enabled = on
            if (!on) setup = repository.twoStepSetup()
        } catch (e: Exception) {
            error = e
        }
    }

    InnerScreen(stringResource(R.string.two_step), onBack) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (enabled) {
                null -> if (error == null) LoadingBox()
                true -> Text(stringResource(R.string.two_step_is_on) + "\n\n" + stringResource(R.string.two_step_disable_prompt),
                    style = MaterialTheme.typography.bodyLarge)
                false -> setup?.let { s ->
                    Text(stringResource(R.string.two_step_step1), style = MaterialTheme.typography.bodyMedium)
                    LimeCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            val qr = remember(s.otpauthUri) { qrBitmap(s.otpauthUri).asImageBitmap() }
                            Image(qr, contentDescription = null, modifier = Modifier.size(180.dp))
                            Text(s.secret.chunked(4).joinToString(" "), style = MoneyStyle.Small,
                                modifier = Modifier.padding(top = 12.dp))
                        }
                    }
                    SecondaryButton(stringResource(R.string.copy_key), {
                        context.getSystemService(ClipboardManager::class.java)
                            .setPrimaryClip(ClipData.newPlainText("key", s.secret))
                        Toast.makeText(context, context.getString(R.string.key_copied), Toast.LENGTH_SHORT).show()
                    }, Modifier.fillMaxWidth())
                    Text(stringResource(R.string.two_step_step2), style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (enabled != null) {
                LimeTextField(code, { code = digits6(it) }, stringResource(R.string.code),
                    keyboardType = KeyboardType.NumberPassword, textStyle = MoneyStyle.Large)
            }
            error?.let { ErrorText(errorMessage(it)) }
        }
        enabled?.let { on ->
            PrimaryButton(
                stringResource(if (on) R.string.turn_off else R.string.turn_on),
                enabled = code.length == 6,
                loading = busy,
                modifier = Modifier.padding(20.dp).navigationBarsPadding(),
                onClick = {
                    scope.launch {
                        busy = true
                        error = null
                        try {
                            if (on) repository.twoStepDisable(code) else repository.twoStepEnable(code)
                            onBack()
                        } catch (e: Exception) {
                            error = e
                            code = ""
                        } finally {
                            busy = false
                        }
                    }
                },
            )
        }
    }
}

/**
 * The server signs every device out after a password change, this one included, so
 * the app signs straight back in with the new password to keep the user going.
 */
@Composable
fun ChangePasswordScreen(onBack: () -> Unit, onSignInAgain: () -> Unit) {
    val context = LocalContext.current
    val container = context.container
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Throwable?>(null) }

    InnerScreen(stringResource(R.string.change_password), onBack) {
        Column(Modifier.weight(1f).imePadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            LimeTextField(current, { current = it }, stringResource(R.string.current_password),
                keyboardType = KeyboardType.Password, visualTransformation = PasswordVisualTransformation())
            LimeTextField(new, { new = it }, stringResource(R.string.new_password),
                keyboardType = KeyboardType.Password, visualTransformation = PasswordVisualTransformation(),
                supportingText = stringResource(R.string.password_rule))
            error?.let { ErrorText(errorMessage(it)) }
        }
        PrimaryButton(
            stringResource(R.string.change_password),
            enabled = current.isNotEmpty() && new.length >= 8,
            loading = busy,
            modifier = Modifier.padding(20.dp).navigationBarsPadding(),
            onClick = {
                scope.launch {
                    busy = true
                    error = null
                    try {
                        container.repository.changePassword(current, new)
                        Toast.makeText(context, context.getString(R.string.password_changed), Toast.LENGTH_SHORT).show()
                        val session = container.session
                        try {
                            session.signIn(session.currentServer(), session.email, new)
                            onBack()
                        } catch (e: ApiException) {
                            session.signOut() // e.g. two-step is on: sign in again normally
                            onSignInAgain()
                        }
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
