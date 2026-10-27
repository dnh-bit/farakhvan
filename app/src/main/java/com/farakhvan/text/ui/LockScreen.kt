package com.farakhvan.text.ui

import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.farakhvan.text.R

/**
 * Optional gate in front of the phone-number database.
 *
 * Uses the device's own credential (fingerprint on Android 9 and older, fingerprint or the
 * screen-lock PIN/pattern on Android 11+). The app never stores a PIN of its own.
 */
@Composable
fun LockScreen(onUnlocked: () -> Unit, onDisableLock: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    val authenticators = remember {
        if (Build.VERSION.SDK_INT >= 30) {
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        }
    }

    val canUnlock = remember(activity) {
        activity != null &&
            BiometricManager.from(activity).canAuthenticate(authenticators) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    var hint by remember { mutableStateOf<String?>(null) }

    fun askDeviceCredential() {
        val act = activity ?: return
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(act.getString(R.string.lock_title))
            .setSubtitle(act.getString(R.string.lock_subtitle))
            .setAllowedAuthenticators(authenticators)
            .build()
        BiometricPrompt(
            act,
            ContextCompat.getMainExecutor(act),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    hint = null
                    onUnlocked()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    hint = errString.toString()
                }
            }
        ).authenticate(info)
    }

    LaunchedEffect(canUnlock) {
        if (canUnlock) askDeviceCredential()
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.lock_title),
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.lock_subtitle),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(24.dp))

            if (canUnlock) {
                Button(onClick = { askDeviceCredential() }) {
                    Text(stringResource(R.string.lock_unlock))
                }
            } else {
                Text(
                    stringResource(R.string.lock_unavailable),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onDisableLock) {
                    Text(stringResource(R.string.lock_disable))
                }
            }

            hint?.let {
                Spacer(Modifier.height(12.dp))
                Text(
                    it,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
