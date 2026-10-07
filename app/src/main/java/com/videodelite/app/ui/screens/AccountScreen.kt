package com.videodelite.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.videodelite.app.AppGraph
import com.videodelite.app.R
import com.videodelite.app.data.DeviceId
import com.videodelite.app.network.AccountDevice
import com.videodelite.app.network.LicenseState
import com.videodelite.app.ui.components.SectionCard
import com.videodelite.app.ui.components.SectionLabel
import com.videodelite.app.ui.components.uiMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AccountScreen(modifier: Modifier = Modifier) {
    val state by AppGraph.account.state.collectAsStateWithLifecycle()
    if (state.loggedIn) {
        LoggedInView(modifier, state)
    } else {
        AuthForms(modifier)
    }
}

// ---------- signed-out flows ----------

@Composable
private fun AuthForms(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var mode by rememberSaveable { mutableStateOf("login") } // login | register | verify
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var invite by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var resendIn by remember { mutableStateOf(0) }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

    LaunchedEffect(resendIn) {
        if (resendIn > 0) {
            delay(1000L)
            resendIn -= 1
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.nav_account), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(R.string.account_logged_out_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )

        SectionLabel(if (mode == "login") stringResource(R.string.account_login) else stringResource(R.string.account_register))
        SectionCard {
            Column(Modifier.padding(16.dp)) {
                if (mode == "register") {
                    OutlinedTextField(
                        value = username, onValueChange = { username = it },
                        label = { Text(stringResource(R.string.account_username)) },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                }
                OutlinedTextField(
                    value = email, onValueChange = { email = it },
                    label = { Text(stringResource(R.string.account_email)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    label = { Text(stringResource(R.string.account_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (mode == "register") {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = invite, onValueChange = { invite = it },
                        label = { Text(stringResource(R.string.account_invite)) },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (mode == "verify") {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(R.string.account_verify_hint, email),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = code, onValueChange = { code = it.take(6) },
                        label = { Text(stringResource(R.string.account_code)) },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(Modifier.height(16.dp))
                TextButton(
                    enabled = !busy && resendIn == 0,
                    onClick = {
                        scope.launch {
                            busy = true
                            try {
                                when (mode) {
                                    "login" -> {
                                        AppGraph.account.login(email, password)
                                    }
                                    "register" -> {
                                        val resp = AppGraph.account.register(username, email, password, invite)
                                        resp.warning?.let { toast(it) }
                                        resp.devVerificationCode?.let { toast("dev code: $it") }
                                        mode = "verify"
                                        resendIn = 60
                                    }
                                    "verify" -> {
                                        AppGraph.account.verifyAndLogin(email, code, password)
                                    }
                                }
                            } catch (e: Exception) {
                                toast(e.uiMessage(context))
                            } finally {
                                busy = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (busy) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text(
                            when (mode) {
                                "login" -> stringResource(R.string.account_login)
                                "register" -> stringResource(R.string.account_register)
                                else -> stringResource(R.string.account_verify)
                            }
                        )
                    }
                }
                if (mode == "verify") {
                    TextButton(onClick = {
                        scope.launch {
                            try {
                                AppGraph.account.resendVerification(email)
                                toast(context.getString(R.string.account_verify_hint, email))
                                resendIn = 60
                            } catch (e: Exception) {
                                toast(e.uiMessage(context))
                            }
                        }
                    }) { Text(stringResource(R.string.account_resend)) }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(vertical = 4.dp))
                TextButton(onClick = {
                    mode = when (mode) {
                        "login" -> "register"
                        else -> "login"
                    }
                }) {
                    Text(
                        if (mode == "login") stringResource(R.string.account_no_account)
                        else stringResource(R.string.account_has_account),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ---------- signed-in flows ----------

private fun licenseLabel(state: LicenseState): Int = when (state) {
    LicenseState.ACTIVE -> R.string.license_active
    LicenseState.REVOKED -> R.string.license_revoked
    LicenseState.EXPIRED -> R.string.license_expired
    else -> R.string.license_none
}

@Composable
private fun LoggedInView(modifier: Modifier = Modifier, state: com.videodelite.app.network.AccountState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var devices by remember { mutableStateOf<List<AccountDevice>?>(null) }
    var reload by remember { mutableStateOf(0) }
    var revokeTarget by remember { mutableStateOf<AccountDevice?>(null) }
    var deleteDialog by remember { mutableStateOf(false) }
    var deletePassword by remember { mutableStateOf("") }

    LaunchedEffect(reload) {
        devices = try {
            AppGraph.account.listDevices()
        } catch (e: Exception) {
            emptyList()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.nav_account), style = MaterialTheme.typography.headlineSmall)

        SectionLabel(stringResource(R.string.nav_account))
        SectionCard {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(44.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        state.username.take(1).uppercase(),
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(state.username, style = MaterialTheme.typography.titleMedium)
                    Text(
                        state.email,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.account_license),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                if (state.activating || state.license == LicenseState.LOADING) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(6.dp))
                    Text(
                        stringResource(R.string.account_activating),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        stringResource(licenseLabel(state.license)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (state.license == LicenseState.ACTIVE) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // Activation can fail on a flaky network; without an
                    // explicit retry the account looks permanently stuck.
                    if (state.license == LicenseState.NONE) {
                        Spacer(Modifier.size(4.dp))
                        TextButton(
                            onClick = {
                                scope.launch {
                                    AppGraph.account.activateDevice()
                                    reload++
                                }
                            },
                        ) { Text(stringResource(R.string.retry)) }
                    }
                }
            }
        }

        SectionLabel(stringResource(R.string.account_devices))
        SectionCard {
            val list = devices
            if (list == null) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                }
            } else if (list.isEmpty()) {
                Text(
                    stringResource(R.string.loading),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                list.forEachIndexed { index, device ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    device.installationId.take(8),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                if (device.installationId == DeviceId.get(context)) {
                                    Spacer(Modifier.size(6.dp))
                                    Text(
                                        stringResource(R.string.device_this),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                            Text(
                                buildString {
                                    device.appVersion?.let { append("v$it") }
                                    device.lastSeen?.let { append(" · ${it.take(16).replace('T', ' ')}") }
                                    if (device.status == "revoked") append(" · ${stringResource(R.string.license_revoked)}")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (device.status != "revoked") {
                            TextButton(onClick = { revokeTarget = device }) {
                                Text(stringResource(R.string.device_revoke), color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    if (index < list.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }
        }

        SectionLabel(stringResource(R.string.settings_about))
        SectionCard {
            TextButton(onClick = {
                scope.launch {
                    try {
                        AppGraph.account.logout()
                    } catch (e: Exception) {
                        Toast.makeText(context, e.uiMessage(context), Toast.LENGTH_LONG).show()
                    }
                }
            }) { Text(stringResource(R.string.account_logout)) }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            TextButton(onClick = { deleteDialog = true }) {
                Text(stringResource(R.string.account_delete), color = MaterialTheme.colorScheme.error)
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    revokeTarget?.let { device ->
        AlertDialog(
            onDismissRequest = { revokeTarget = null },
            title = { Text(stringResource(R.string.device_revoke)) },
            text = { Text(stringResource(R.string.device_revoke_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    revokeTarget = null
                    scope.launch {
                        try {
                            AppGraph.account.revokeDevice(device.id)
                            if (device.installationId == DeviceId.get(context)) {
                                AppGraph.account.activateDevice()
                            }
                            reload++
                        } catch (e: Exception) {
                            Toast.makeText(context, e.uiMessage(context), Toast.LENGTH_LONG).show()
                        }
                    }
                }) { Text(stringResource(R.string.confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { revokeTarget = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (deleteDialog) {
        AlertDialog(
            onDismissRequest = { deleteDialog = false },
            title = { Text(stringResource(R.string.account_delete_confirm_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.account_delete_confirm_body))
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = deletePassword,
                        onValueChange = { deletePassword = it },
                        label = { Text(stringResource(R.string.account_password)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = deletePassword.length >= 8,
                    onClick = {
                        deleteDialog = false
                        scope.launch {
                            try {
                                AppGraph.account.deleteAccount(deletePassword)
                            } catch (e: Exception) {
                                Toast.makeText(context, e.uiMessage(context), Toast.LENGTH_LONG).show()
                            } finally {
                                deletePassword = ""
                            }
                        }
                    },
                ) { Text(stringResource(R.string.confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}
