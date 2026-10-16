package com.dfdx047.dragonburst

import android.app.Application
import com.dfdx047.dragonburst.data.AppPaths

class DragonBurstApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppPaths.init(this)
    }

    companion object {
        /** Repositorio do port (pagina "Sobre" e verificacao de atualizacoes). */
        const val GITHUB_OWNER = "dfdx047"
        const val GITHUB_REPO = "Dragon-Burst"
        const val GITHUB_URL = "https://github.com/$GITHUB_OWNER/$GITHUB_REPO"
        const val DEVELOPER = "dfdx047"
        const val DISCORD_URL = "https://discord.gg/nXSANX9V6k"
        const val YOUTUBE_URL = "https://www.youtube.com/@DFDX47"
        const val KOFI_URL = "https://ko-fi.com/dfdx047"
        const val ORIGINAL_PROJECT_URL = "https://github.com/iExplosiveRage/DBZ-Burst-Limit-Recompiled"
    }
}
