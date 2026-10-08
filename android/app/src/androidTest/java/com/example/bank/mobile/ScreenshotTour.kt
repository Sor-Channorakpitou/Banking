package com.example.bank.mobile

import android.content.Intent
import android.os.ParcelFileDescriptor
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Walks through the app like a customer and saves a screenshot of every main screen
 * to /sdcard/lime-shots. Expects the bank API on the host (10.0.2.2:8080) seeded by
 * .github/scripts/seed-demo.sh; run by .github/workflows/android-screenshots.yml.
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotTour {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val receiver: String = InstrumentationRegistry.getArguments().getString("receiver") ?: "10000000017"

    @Test
    fun tour() {
        shell("mkdir -p /sdcard/lime-shots")

        // Sign in
        waitForText("Welcome to Lime")
        shot("01_sign_in")
        textFields()[0].performTextInput("sopheak@example.com")
        textFields()[1].performTextInput("password123")
        rule.onAllNodesWithText("Sign in").filter(hasClickAction()).onFirst().performClick()

        // Create the PIN (typed twice)
        waitForText("Create a 6-digit PIN")
        listOf("2", "5", "8").forEach(::tap)
        shot("02_create_pin")
        listOf("0", "4", "6").forEach(::tap)
        waitForText("Enter the PIN again")
        listOf("2", "5", "8", "0", "4", "6").forEach(::tap)

        // Home
        waitForText("Available balance")
        waitForText("Salary")
        shot("03_home")

        // Transfer with the recipient check
        rule.onAllNodesWithText("Transfer").onFirst().performClick()
        waitForText("New transfer")
        textFields()[0].performTextInput(receiver)
        textFields()[1].performTextInput("30.25")
        textFields()[2].performTextInput("Dinner")
        waitForText("DARA C.", substring = true)
        hideKeyboard()
        shot("04_transfer")
        back()

        // Exchange with a live quote
        waitForText("Available balance")
        rule.onAllNodesWithText("Exchange").onFirst().performClick()
        waitForText("You sell")
        textFields()[0].performTextInput("100")
        waitForText("Rate applied")
        hideKeyboard()
        shot("05_exchange")
        back()

        // My QR
        waitForText("Available balance")
        rule.onAllNodesWithText("My QR").onFirst().performClick()
        waitForText("Scan this with Lime", substring = true)
        Thread.sleep(800)
        shot("06_my_qr")
        back()

        // Monthly statement
        waitForText("Available balance")
        rule.onAllNodesWithText("Statements").onFirst().performClick()
        waitForText("Opening balance")
        shot("07_statement")
        back()

        // Accounts tab and one account's history
        waitForText("Available balance")
        rule.onAllNodesWithText("Accounts").onLast().performClick() // the bottom bar entry
        waitForText("My accounts")
        waitForText("KHR account")
        shot("08_accounts")
        rule.onAllNodesWithText("USD account").onFirst().performClick()
        waitForText("Money out")
        shot("09_account_history")
        back()

        // Payees tab
        waitForText("My accounts")
        rule.onAllNodesWithText("Payees").onLast().performClick()
        waitForText("Add payee")
        waitForText("Dara")
        shot("10_payees")

        // Profile and two-step setup
        rule.onAllNodesWithText("Profile").onLast().performClick()
        waitForText("Log out")
        Thread.sleep(800) // two-step state and today's limit load
        shot("11_profile")
        rule.onAllNodesWithText("Two-step verification").onFirst().performClick()
        waitForText("Turn on")
        Thread.sleep(500)
        shot("12_two_step")
        back()

        // Reopen the app: it asks for the PIN
        waitForText("Log out")
        // A cold relaunch (new task), not a recreate: a recreate would restore the last screen.
        instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        waitForText("Welcome back")
        listOf("2", "5", "8").forEach(::tap)
        shot("13_unlock")
        listOf("0", "4", "6").forEach(::tap)
        waitForText("Available balance")

        // Khmer
        rule.onAllNodesWithText("ខ្មែរ | EN").onFirst().performClick()
        waitForText("សមតុល្យដែលអាចប្រើបាន")
        Thread.sleep(1500)
        shot("14_home_khmer")
    }

    // ------------------------------------------------------------------ helpers

    private fun textFields(): List<SemanticsNodeInteraction> {
        val nodes = rule.onAllNodes(hasSetTextAction())
        val count = nodes.fetchSemanticsNodes().size
        return (0 until count).map { nodes[it] }
    }

    private fun tap(text: String) {
        rule.onAllNodesWithText(text).onFirst().performClick()
        rule.waitForIdle()
    }

    private fun waitForText(text: String, substring: Boolean = false) {
        rule.waitUntil(timeoutMillis = 30_000) {
            rule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun back() {
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
    }

    private fun hideKeyboard() {
        rule.runOnUiThread {
            val imm = rule.activity.getSystemService(InputMethodManager::class.java)
            imm.hideSoftInputFromWindow(rule.activity.window.decorView.windowToken, 0)
        }
        Thread.sleep(400)
    }

    /** A real screen capture, status bar included, as the user would see it. */
    private fun shot(name: String) {
        rule.waitForIdle()
        Thread.sleep(600) // let the last frame settle
        shell("screencap -p /sdcard/lime-shots/$name.png")
    }

    /** Runs a shell command and waits for it to finish. */
    private fun shell(command: String) {
        val pfd: ParcelFileDescriptor = instrumentation.uiAutomation.executeShellCommand(command)
        ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes() }
    }
}
