package com.prismora.player

import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.prismora.player.ui.MikuGlassApp

class MainActivity : ComponentActivity() {
    private val askPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val audioPermission = if (Build.VERSION.SDK_INT >= 33) {
            android.Manifest.permission.READ_MEDIA_AUDIO
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }
        val audioGranted = grants[audioPermission]
            ?: (ContextCompat.checkSelfPermission(this, audioPermission) == PackageManager.PERMISSION_GRANTED)
        if (audioGranted) vm?.scanMediaStore() else vm?.permissionDenied()
    }

    private val pickFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            runCatching { contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            vm?.importFolder(it)
        }
    }

    private var vm: PlayerViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestHighestRefreshRate()
        setContent {
            val model: PlayerViewModel = viewModel()
            vm = model
            MikuGlassApp(model, onPickFolder = { pickFolder.launch(null) })
        }
        window.decorView.post { requestRuntimePermissions() }
    }

    private fun requestRuntimePermissions() {
        val audioPermission = if (Build.VERSION.SDK_INT >= 33) {
            android.Manifest.permission.READ_MEDIA_AUDIO
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }
        val missing = buildList {
            if (ContextCompat.checkSelfPermission(this@MainActivity, audioPermission) != PackageManager.PERMISSION_GRANTED) {
                add(audioPermission)
            }
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(this@MainActivity, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                add(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (missing.isNotEmpty()) {
            askPermissions.launch(missing.toTypedArray())
        } else {
            vm?.scanMediaStore()
        }
    }

    private fun requestHighestRefreshRate() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val screen = windowManager.defaultDisplay
        val current = screen.mode
        val fastest = screen.supportedModes
            .asSequence()
            .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
            .maxByOrNull { it.refreshRate }
            ?: current
        window.attributes = window.attributes.apply {
            preferredDisplayModeId = fastest.modeId
            preferredRefreshRate = fastest.refreshRate
        }
    }

    override fun onResume() {
        super.onResume()
        vm?.refreshOutputs()
    }
}
