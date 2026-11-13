package com.kenkern.engine

import android.app.Application
import com.kenkern.engine.core.KenkernBuiltInClasses

/** Application da KENKERN: registra as classes/funções nativas da engine. */
class KenkernApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        KenkernBuiltInClasses.registerAll()
    }
}
