package com.videodelite.app

import android.app.Application
import com.videodelite.app.data.AppDatabase
import com.videodelite.app.data.SettingsRepository
import com.videodelite.app.data.TokenStore
import com.videodelite.app.media.Analyzer
import com.videodelite.app.media.CompressEngine
import com.videodelite.app.media.TaskManager
import com.videodelite.app.network.AccountClient
import com.videodelite.app.network.ApiFactory
import com.videodelite.app.network.AuthStack
import com.videodelite.app.network.VdApi

/** Tiny service locator; enough for an app this size without DI tooling. */
object AppGraph {
    lateinit var settings: SettingsRepository
        private set
    lateinit var db: AppDatabase
        private set
    lateinit var tokenStore: TokenStore
        private set
    lateinit var account: AccountClient
        private set
    lateinit var analyzer: Analyzer
        private set
    lateinit var engine: CompressEngine
        private set
    lateinit var taskManager: TaskManager
        private set

    fun init(app: Application) {
        settings = SettingsRepository(app)
        db = AppDatabase.get(app)
        tokenStore = TokenStore(app)

        val rawApi = ApiFactory.api(ApiFactory.rawClient())
        val authStack = AuthStack(tokenStore, rawApi)
        val api = ApiFactory.api(authStack.client)
        account = AccountClient(api, rawApi, tokenStore, authStack, app)

        analyzer = Analyzer(app)
        engine = CompressEngine(app)
        taskManager = TaskManager(app, analyzer, engine)
    }
}
