package com.kenkern.engine

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.kenkern.engine.ui.AppLanguage
import com.kenkern.engine.ui.KenkernAppRoot
import com.kenkern.engine.ui.theme.KenkernTheme

/**
 * Activity única da KENKERN. Toda a UI é Jetpack Compose (Material 3);
 * o viewport 3D é um GLSurfaceView embutido via AndroidView.
 *
 * T0.1 (idiomas en/pt): o idioma do app (escolha do usuário ou padrão do
 * aparelho) é aplicado ANTES do onCreate via attachBaseContext — todos os
 * recursos (strings) carregam já no idioma certo; a troca no seletor do Hub
 * chama recreate() e passa por aqui de novo.
 */
class MainActivity : ComponentActivity() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLanguage.wrap(base))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KenkernTheme {
                KenkernAppRoot()
            }
        }
    }
}
