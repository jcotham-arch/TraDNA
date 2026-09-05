package com.tradna.APP.lab

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class BackendConnection(val baseUrl: String, val token: String)

@Composable
fun BackendConnectionScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val saved = remember { BackendConnectionStore.load(context) }
    var baseUrl by remember { mutableStateOf(saved?.baseUrl.orEmpty()) }
    var token by remember { mutableStateOf(saved?.token.orEmpty()) }
    var message by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Color(0xFF07090D)).padding(20.dp)) {
        Button(onClick = onBack) { Text("BACK") }
        Spacer(Modifier.height(18.dp))
        Text("SECURE BACKEND", color = Color(0xFF72E7FF))
        Text("Connect this phone", color = Color.White)
        Spacer(Modifier.height(8.dp))
        Text(
            "Android connects to TraDNA's backend. Robinhood OAuth remains on the server.",
            color = Color(0xFF8D98A8)
        )
        Spacer(Modifier.height(18.dp))
        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            label = { Text("Backend HTTPS URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("Device API token") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(14.dp))
        Button(
            enabled = !testing,
            onClick = {
                testing = true
                scope.launch {
                    val connection = BackendConnection(baseUrl.trim(), token.trim())
                    val result = runCatching { BackendStatusClient.fetch(connection) }
                    message = result.fold(
                        onSuccess = {
                            BackendConnectionStore.save(context, connection)
                            "Connected • ${it.first} • ${it.second} quotes • execution disabled"
                        },
                        onFailure = { it.message ?: "Connection failed." }
                    )
                    testing = false
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (testing) "TESTING…" else "TEST & SAVE") }
        message?.let { Text(it, color = Color(0xFF39D6A0), modifier = Modifier.padding(top = 12.dp)) }
        Spacer(Modifier.height(18.dp))
        Text(
            "The device token is encrypted with Android Keystore. Robinhood credentials are never stored here.",
            color = Color(0xFF8D98A8)
        )
    }
}

private object BackendStatusClient {
    suspend fun fetch(connection: BackendConnection): Pair<String, Int> = withContext(Dispatchers.IO) {
        val uri = URI(connection.baseUrl)
        require(uri.scheme == "https" || uri.host in setOf("127.0.0.1", "localhost")) {
            "The backend must use HTTPS."
        }
        require(connection.token.length >= 32) { "The device token must contain at least 32 characters." }
        val http = URL(connection.baseUrl.trimEnd('/') + "/v1/live/status").openConnection() as HttpURLConnection
        try {
            http.connectTimeout = 10_000
            http.readTimeout = 15_000
            http.setRequestProperty("Authorization", "Bearer ${connection.token}")
            require(http.responseCode == 200) { "Backend returned HTTP ${http.responseCode}." }
            val json = JSONObject(http.inputStream.bufferedReader().use { it.readText() })
            Pair(
                json.optString("last_sync_status").ifBlank { json.getString("connection") },
                json.optJSONArray("quotes")?.length() ?: 0
            )
        } finally {
            http.disconnect()
        }
    }
}

private object BackendConnectionStore {
    private const val PREFS = "tradna_backend_connection"
    private const val ALIAS = "tradna_backend_device_token"

    fun save(context: Context, value: BackendConnection) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(value.token.toByteArray())
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("url", value.baseUrl)
            .putString("token", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP)).apply()
    }

    fun load(context: Context): BackendConnection? = runCatching {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val url = prefs.getString("url", null) ?: return null
        val encrypted = Base64.decode(prefs.getString("token", null) ?: return null, Base64.NO_WRAP)
        val iv = Base64.decode(prefs.getString("iv", null) ?: return null, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        BackendConnection(url, cipher.doFinal(encrypted).toString(Charsets.UTF_8))
    }.getOrNull()

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build()
        )
        return generator.generateKey()
    }
}
