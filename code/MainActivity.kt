package app.captunes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.captunes.ui.MainViewModel
import app.captunes.ui.login.LoginScreen
import app.captunes.ui.shell.AppShell
import app.captunes.ui.theme.CapTunesTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            CapTunesTheme {
                val vm: MainViewModel = viewModel()
                val session by vm.session.collectAsStateWithLifecycle()
                if (session == null) LoginScreen(vm) else AppShell(vm)
            }
        }
    }
}
