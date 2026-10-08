package com.example.bank.mobile.ui

import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.bank.mobile.R
import com.example.bank.mobile.container
import com.example.bank.mobile.data.StartPoint
import com.example.bank.mobile.ui.screens.AccountDetailScreen
import com.example.bank.mobile.ui.screens.AccountsScreen
import com.example.bank.mobile.ui.screens.ComingSoonScreen
import com.example.bank.mobile.ui.screens.CreatePinScreen
import com.example.bank.mobile.ui.screens.ExchangeScreen
import com.example.bank.mobile.ui.screens.HomeScreen
import com.example.bank.mobile.ui.screens.OpenAccountScreen
import com.example.bank.mobile.ui.screens.ProfileScreen
import com.example.bank.mobile.ui.screens.ReceiveScreen
import com.example.bank.mobile.ui.screens.SignInScreen
import com.example.bank.mobile.ui.screens.StatementScreen
import com.example.bank.mobile.ui.screens.TransferScreen
import com.example.bank.mobile.ui.screens.UnlockScreen
import com.example.bank.mobile.ui.theme.Lime

private object Routes {
    const val SIGN_IN = "sign-in"
    const val CREATE_PIN = "create-pin"
    const val UNLOCK = "unlock"
    const val HOME = "home"
    const val ACCOUNTS = "accounts"
    const val PROFILE = "profile"
    const val ACCOUNT = "account/{id}"
    const val STATEMENT = "statement/{id}"
    const val RECEIVE = "receive/{id}"
    const val TRANSFER = "transfer?from={from}"
    const val EXCHANGE = "exchange"
    const val OPEN_ACCOUNT = "open-account"
    const val COMING_SOON = "coming-soon/{feature}"
}

/** Goes to [route] and forgets everything before it (after sign-in/out, unlock). */
private fun NavHostController.restartAt(route: String) = navigate(route) {
    popUpTo(0) { inclusive = true }
    launchSingleTop = true
}

@Composable
fun AppNavigation(start: StartPoint) {
    val nav = rememberNavController()
    val session = LocalContext.current.container.session

    // Refresh token expired or revoked: back to sign-in from wherever we are.
    LaunchedEffect(Unit) { session.signedOut.collect { nav.restartAt(Routes.SIGN_IN) } }

    val startRoute = when (start) {
        StartPoint.SIGN_IN -> Routes.SIGN_IN
        StartPoint.CREATE_PIN -> Routes.CREATE_PIN
        StartPoint.UNLOCK -> Routes.UNLOCK
    }
    val comingSoon: (String) -> Unit = { nav.navigate("coming-soon/" + Uri.encode(it)) }
    val backStack by nav.currentBackStackEntryAsState()
    val tab = backStack?.destination?.route

    Column(Modifier.fillMaxSize()) {
        NavHost(nav, startDestination = startRoute, modifier = Modifier.weight(1f)) {
            composable(Routes.SIGN_IN) { SignInScreen(onSignedIn = { nav.restartAt(Routes.CREATE_PIN) }) }
            composable(Routes.CREATE_PIN) {
                CreatePinScreen(onDone = {
                    if (nav.previousBackStackEntry != null) nav.popBackStack() else nav.restartAt(Routes.HOME)
                })
            }
            composable(Routes.UNLOCK) {
                UnlockScreen(onUnlocked = { nav.restartAt(Routes.HOME) }, onSignInAgain = { nav.restartAt(Routes.SIGN_IN) })
            }
            composable(Routes.HOME) {
                HomeScreen(
                    onTransfer = { nav.navigate("transfer") },
                    onExchange = { nav.navigate(Routes.EXCHANGE) },
                    onMyQr = { nav.navigate("receive/$it") },
                    onAccounts = { nav.navigateTab(Routes.ACCOUNTS) },
                    onAccount = { nav.navigate("account/$it") },
                    onStatements = { nav.navigate("statement/$it") },
                    onOpenAccount = { nav.navigate(Routes.OPEN_ACCOUNT) },
                    onComingSoon = comingSoon,
                    onProfile = { nav.navigateTab(Routes.PROFILE) },
                )
            }
            composable(Routes.ACCOUNTS) {
                AccountsScreen(onAccount = { nav.navigate("account/$it") }, onOpenAccount = { nav.navigate(Routes.OPEN_ACCOUNT) })
            }
            composable(Routes.PROFILE) {
                ProfileScreen(onChangePin = { nav.navigate(Routes.CREATE_PIN) }, onSignedOut = { nav.restartAt(Routes.SIGN_IN) })
            }
            composable(Routes.ACCOUNT, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                val id = entry.arguments!!.getLong("id")
                AccountDetailScreen(
                    accountId = id,
                    onBack = { nav.popBackStack() },
                    onStatement = { nav.navigate("statement/$it") },
                    onShareQr = { nav.navigate("receive/$it") },
                )
            }
            composable(Routes.STATEMENT, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                StatementScreen(entry.arguments!!.getLong("id"), onBack = { nav.popBackStack() })
            }
            composable(Routes.RECEIVE, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                ReceiveScreen(entry.arguments!!.getLong("id"), onBack = { nav.popBackStack() })
            }
            composable(
                Routes.TRANSFER,
                arguments = listOf(navArgument("from") { type = NavType.LongType; defaultValue = -1L }),
            ) { entry ->
                val from = entry.arguments?.getLong("from")?.takeIf { it > 0 }
                TransferScreen(fromAccountId = from, onBack = { nav.popBackStack() })
            }
            composable(Routes.EXCHANGE) {
                ExchangeScreen(onBack = { nav.popBackStack() }, onOpenAccount = { nav.navigate(Routes.OPEN_ACCOUNT) })
            }
            composable(Routes.OPEN_ACCOUNT) {
                OpenAccountScreen(onBack = { nav.popBackStack() }, onOpened = { id ->
                    nav.popBackStack()
                    nav.navigate("account/$id")
                })
            }
            composable(Routes.COMING_SOON) { entry ->
                ComingSoonScreen(entry.arguments?.getString("feature").orEmpty(), onBack = { nav.popBackStack() })
            }
        }

        if (tab in setOf(Routes.HOME, Routes.ACCOUNTS, Routes.PROFILE)) {
            BottomBar(
                current = tab,
                onTab = { nav.navigateTab(it) },
                onScan = comingSoon,
            )
        }
    }
}

/** Switching tabs keeps one copy of each tab instead of stacking them up. */
private fun NavHostController.navigateTab(route: String) = navigate(route) {
    popUpTo(Routes.HOME) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

private data class Tab(val route: String?, @DrawableRes val icon: Int, @StringRes val label: Int)

@Composable
private fun BottomBar(current: String?, onTab: (String) -> Unit, onScan: (String) -> Unit) {
    val scanLabel = stringResource(R.string.nav_scan)
    val payeesLabel = stringResource(R.string.nav_payees)
    val tabs = listOf(
        Tab(Routes.HOME, R.drawable.ic_home, R.string.nav_home),
        Tab(Routes.ACCOUNTS, R.drawable.ic_card, R.string.nav_accounts),
        Tab(null, R.drawable.ic_scan, R.string.nav_scan),
        Tab("payees", R.drawable.ic_people, R.string.nav_payees),
        Tab(Routes.PROFILE, R.drawable.ic_user, R.string.nav_profile),
    )
    Column(Modifier.background(Color.White).navigationBarsPadding()) {
        HorizontalDivider(color = Lime.Border)
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.Bottom,
        ) {
            tabs.forEach { tab ->
                val selected = tab.route == current
                Column(
                    Modifier
                        .weight(1f)
                        .clickable(role = Role.Tab) {
                            when (tab.route) {
                                null -> onScan(scanLabel)
                                "payees" -> onScan(payeesLabel)
                                else -> onTab(tab.route)
                            }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (tab.route == null) {
                        // The raised Scan QR button in the middle.
                        Box(
                            Modifier
                                .offset(y = (-14).dp)
                                .size(58.dp)
                                .clip(CircleShape)
                                .background(Lime.Green)
                                .border(4.dp, Lime.Background, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(painterResource(tab.icon), null, tint = Color.White, modifier = Modifier.size(26.dp))
                        }
                        Text(stringResource(tab.label), style = MaterialTheme.typography.labelSmall, color = Lime.Ink,
                            modifier = Modifier.offset(y = (-10).dp))
                    } else {
                        Icon(painterResource(tab.icon), null, tint = if (selected) Lime.Green else Lime.Muted,
                            modifier = Modifier.size(22.dp))
                        Text(
                            stringResource(tab.label),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = if (selected) Lime.Green else Lime.Muted,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                }
            }
        }
    }
}
