package com.kenkern.engine.fluxo

import android.app.Application
import com.kenkern.engine.core.KenkernBuiltInClasses
import com.kenkern.engine.core.NickKindCatalog

/**
 * Application do teste Robolectric: registra o MESMO catálogo de classes e
 * kinds do app, mas NÃO instala o handler nativo de sinais do
 * [com.kenkern.engine.KenkernApplication] — na JVM de teste o HotSpot usa
 * SIGSEGV para checagem implícita de nulo, e o handler mataria o processo
 * (exit 139, o mesmo defeito do E1).
 */
class FluxoRealTestApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        KenkernBuiltInClasses.registerAll()
        NickKindCatalog.registerAll()
    }
}
