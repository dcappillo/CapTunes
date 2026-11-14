package app.captunes.ui.login

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.platform.LocalContext
import app.captunes.data.LocalSource
import app.captunes.ui.components.BoltYellow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.captunes.ui.MainViewModel
import app.captunes.ui.components.CapTunesLogo
import app.captunes.ui.components.Wordmark
import app.captunes.ui.theme.Coral
import app.captunes.ui.theme.TextDim

@Composable
fun LoginScreen(vm: MainViewModel) {
    val state by vm.login.collectAsStateWithLifecycle()
    var server by rememberSaveable { mutableStateOf("") }
    var user by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val submit = {
        focus.clearFocus()
        vm.signIn(server, user, password)
    }
    val context = LocalContext.current
    val askForMusic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.useLocalLibrary() else vm.localPermissionDenied()
    }
    val useLocal = {
        if (LocalSource.hasPermission(context)) vm.useLocalLibrary() else askForMusic.launch(LocalSource.permission)
    }

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(48.dp))
        CapTunesLogo(Modifier.size(96.dp))
        Wordmark(size = 34.sp, showMark = false)
        Text(
            "Your music, your way.",
            color = TextDim,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(12.dp))

        Button(
            onClick = { useLocal() },
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = BoltYellow, contentColor = Color.Black),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Icon(Icons.Default.PhoneAndroid, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text("Use music on this phone", style = MaterialTheme.typography.titleMedium)
        }
        Text(
            "Plays the songs already stored on your phone or tablet. No account needed.",
            color = TextDim,
            style = MaterialTheme.typography.bodySmall,
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
            HorizontalDivider(Modifier.weight(1f), color = TextDim.copy(alpha = 0.3f))
            Text(
                "or connect your own server",
                color = TextDim,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            HorizontalDivider(Modifier.weight(1f), color = TextDim.copy(alpha = 0.3f))
        }

        OutlinedTextField(
            value = server,
            onValueChange = { server = it },
            label = { Text("Navidrome address") },
            placeholder = { Text("https://music.example.com") },
            supportingText = { Text("Navidrome or any Subsonic server") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = user,
            onValueChange = { user = it },
            label = { Text("Username") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (showPassword) "Hide password" else "Show password",
                    )
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { submit() }),
            modifier = Modifier.fillMaxWidth(),
        )

        state.error?.let {
            Text(it, color = Coral, style = MaterialTheme.typography.bodyMedium)
        }

        Button(
            onClick = { submit() },
            enabled = !state.loading,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = Coral, contentColor = Color.Black),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            if (state.loading) {
                CircularProgressIndicator(Modifier.size(22.dp), color = Color.Black, strokeWidth = 2.dp)
            } else {
                Text("Sign in", style = MaterialTheme.typography.titleMedium)
            }
        }
        Text(
            "Your password is never stored. Cap Tunes keeps only a one-way token.",
            color = TextDim,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(24.dp))
    }
}
