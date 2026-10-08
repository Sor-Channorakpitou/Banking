package com.example.bank.mobile

import android.app.Application
import android.content.Context
import com.example.bank.mobile.data.BankRepository
import com.example.bank.mobile.data.Network
import com.example.bank.mobile.data.ServerAddress
import com.example.bank.mobile.data.SessionManager
import com.example.bank.mobile.data.SessionStore

/**
 * Builds the app's few long-lived objects once. A hand-written container instead of
 * a DI framework: with this many objects, plain constructors are easier to follow.
 */
class AppContainer(context: Context) {
    private val server = ServerAddress(BuildConfig.DEFAULT_SERVER_URL)
    private val store = SessionStore(context)

    // Network and SessionManager need each other (the authenticator refreshes through the
    // session; the session calls the API), so each gets the other through a lambda.
    lateinit var session: SessionManager
        private set
    private val network = Network(server, accessToken = { session.accessToken }, session = { session })

    init {
        session = SessionManager(store, server, publicApi = { network.publicApi }, api = { network.api })
    }

    val repository = BankRepository { network.api }
}

class LimeApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

val Context.container: AppContainer get() = (applicationContext as LimeApp).container
