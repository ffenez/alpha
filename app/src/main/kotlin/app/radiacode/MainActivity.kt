package app.radiacode

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.radiacode.ui.AppRoot
import app.radiacode.ui.theme.PixelTheme

/** Single activity; all screens are Compose under [AppRoot]. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            PixelTheme {
                AppRoot(AppGraph.get(this))
            }
        }
    }
}
