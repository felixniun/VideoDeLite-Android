package com.videodelite.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.videodelite.app.AppGraph
import com.videodelite.app.R
import com.videodelite.app.core.Format
import com.videodelite.app.media.ProConfig
import com.videodelite.app.media.Quality
import com.videodelite.app.media.VideoCodec
import com.videodelite.app.media.VideoInfo
import com.videodelite.app.network.LicenseState
import com.videodelite.app.ui.components.SectionCard
import com.videodelite.app.ui.components.SectionLabel
import com.videodelite.app.ui.components.VdSegmented
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

class HomeViewModel(app: android.app.Application) : AndroidViewModel(app) {

    data class Pending(
        val id: Long,
        val uri: Uri,
        val info: VideoInfo? = null,
        val failed: Boolean = false,
    )

    private val nextId = AtomicLong(1)

    private val _pending = MutableStateFlow<List<Pending>>(emptyList())
    val pending: StateFlow<List<Pending>> = _pending.asStateFlow()

    private val _codec = MutableStateFlow(VideoCodec.H264)
    val codec: StateFlow<VideoCodec> = _codec.asStateFlow()

    private val _quality = MutableStateFlow(Quality.MID)
    val quality: StateFlow<Quality> = _quality.asStateFlow()

    private val _hevcAvailable = MutableStateFlow(true)
    val hevcAvailable: StateFlow<Boolean> = _hevcAvailable.asStateFlow()

    /** simple | professional — professional is gated on an active license. */
    private val _mode = MutableStateFlow("simple")
    val mode: StateFlow<String> = _mode.asStateFlow()

    private val _pro = MutableStateFlow(ProConfig())
    val pro: StateFlow<ProConfig> = _pro.asStateFlow()

    init {
        viewModelScope.launch {
            _hevcAvailable.value = AppGraph.engine.hasHevcEncoder()
            val s = AppGraph.settings.current()
            _codec.value = if (s.defaultCodec == "h265") VideoCodec.H265 else VideoCodec.H264
            _quality.value = Quality.entries.firstOrNull { it.id == s.defaultQuality } ?: Quality.MID
            _pro.value = _pro.value.copy(
                codec = _codec.value,
                audioBitrateKbps = 128,
            )
        }
    }

    fun setCodec(c: VideoCodec) {
        _codec.value = c
        // Keep the professional panel's codec in step with Simple mode so
        // switching modes never silently changes the encoder.
        _pro.value = _pro.value.copy(codec = c)
    }

    fun setQuality(q: Quality) { _quality.value = q }

    fun setMode(m: String) { _mode.value = m }

    fun setPro(cfg: ProConfig) { _pro.value = cfg }

    fun addUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val added = uris.map { Pending(nextId.getAndIncrement(), it) }
        _pending.value = _pending.value + added
        added.forEach { item ->
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val info = AppGraph.analyzer.analyze(item.uri)
                    _pending.value = _pending.value.map {
                        if (it.id == item.id) it.copy(info = info) else it
                    }
                } catch (e: Exception) {
                    _pending.value = _pending.value.map {
                        if (it.id == item.id) it.copy(failed = true) else it
                    }
                }
            }
        }
    }

    fun remove(id: Long) {
        _pending.value = _pending.value.filter { it.id != id }
    }

    fun start(unlocked: Boolean, onQueued: () -> Unit) {
        val uris = _pending.value.filter { it.info != null }.map { it.info!!.uri }
        if (uris.isEmpty()) return
        val professional = _mode.value == "professional" && unlocked &&
            ProConfig.error(_pro.value) == null
        if (professional) {
            val cfg = _pro.value
            AppGraph.taskManager.enqueue(uris, cfg.codec, _quality.value, cfg)
        } else {
            AppGraph.taskManager.enqueue(uris, _codec.value, _quality.value)
        }
        _pending.value = emptyList()
        onQueued()
    }
}

@Composable
fun HomeScreen(modifier: Modifier = Modifier, onStarted: () -> Unit, onGoAccount: () -> Unit) {
    val context = LocalContext.current
    val vm: HomeViewModel = viewModel()
    val pending by vm.pending.collectAsState()
    val codec by vm.codec.collectAsState()
    val quality by vm.quality.collectAsState()
    val hevcAvailable by vm.hevcAvailable.collectAsState()
    val mode by vm.mode.collectAsState()
    val pro by vm.pro.collectAsState()
    val parallelLimit by AppGraph.taskManager.parallelLimit.collectAsState()
    val accountState by AppGraph.account.state.collectAsStateWithLifecycle()

    val readyCount = pending.count { it.info != null }

    // Professional mode is unlocked by an active license, mirroring the
    // desktop's rule: account state gates starting a *new* task only and never
    // touches compression already running.
    val unlocked = accountState.license == LicenseState.ACTIVE
    val proMode = mode == "professional"
    val proError = ProConfig.error(pro)
    val canStart = readyCount > 0 && (!proMode || (unlocked && proError == null))

    val beginStart: () -> Unit = {
        if (canStart) {
            vm.start(unlocked) {
                Toast.makeText(context, context.getString(R.string.home_start_toast), Toast.LENGTH_SHORT).show()
                onStarted()
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val storageOk = Build.VERSION.SDK_INT >= 29 ||
            result[Manifest.permission.WRITE_EXTERNAL_STORAGE] == true
        if (storageOk) beginStart()
    }

    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 20),
    ) { uris -> vm.addUris(uris) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Text("VideoDelite", style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(R.string.home_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionLabel(stringResource(R.string.home_mode))
        SectionCard {
            VdSegmented(
                options = listOf("simple", "professional"),
                selected = mode,
                label = {
                    if (it == "simple") stringResource(R.string.mode_simple)
                    else stringResource(R.string.mode_professional)
                },
                onSelect = vm::setMode,
            )
        }

        if (proMode && !unlocked) {
            SectionCard {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.pro_locked),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onGoAccount, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.pro_go_login))
                    }
                }
            }
        }

        if (proMode && unlocked) {
            ProPanel(cfg = pro, hevcAvailable = hevcAvailable, onChange = vm::setPro)
        }

        if (!proMode) {
        SectionLabel(stringResource(R.string.home_codec))
        SectionCard {
            VdSegmented(
                options = VideoCodec.entries.toList(),
                selected = codec,
                label = { it.name },
                sublabel = {
                    when (it) {
                        VideoCodec.H264 -> stringResource(R.string.home_codec_h264_desc)
                        VideoCodec.H265 -> stringResource(R.string.home_codec_h265_desc)
                    }
                },
                enabled = { it != VideoCodec.H265 || hevcAvailable },
                onSelect = vm::setCodec,
            )
            if (!hevcAvailable) {
                Text(
                    stringResource(R.string.home_codec_h265_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }

        SectionLabel(stringResource(R.string.home_quality))
        SectionCard {
            VdSegmented(
                options = Quality.entries.toList(),
                selected = quality,
                label = {
                    when (it) {
                        Quality.LOW -> stringResource(R.string.quality_low)
                        Quality.MID -> stringResource(R.string.quality_mid)
                        Quality.HIGH -> stringResource(R.string.quality_high)
                    }
                },
                sublabel = {
                    when (it) {
                        Quality.LOW -> stringResource(R.string.quality_low_desc)
                        Quality.MID -> stringResource(R.string.quality_mid_desc)
                        Quality.HIGH -> stringResource(R.string.quality_high_desc)
                    }
                },
                onSelect = vm::setQuality,
            )
        }
        }

        SectionLabel(stringResource(R.string.home_pick))
        SectionCard {
            if (pending.isEmpty()) {
                Text(
                    stringResource(R.string.home_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                pending.forEach { item ->
                    PendingRow(item = item, onRemove = { vm.remove(item.id) })
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                pickLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
                )
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Text(stringResource(R.string.home_pick))
        }
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = {
                val needed = buildList {
                    if (Build.VERSION.SDK_INT < 29 &&
                        ContextCompat.checkSelfPermission(
                            context, Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    }
                    if (Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(
                            context, Manifest.permission.POST_NOTIFICATIONS,
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                if (needed.isEmpty()) beginStart() else permissionLauncher.launch(needed.toTypedArray())
            },
            enabled = canStart,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.home_start))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.home_output_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.home_parallel_hint, parallelLimit),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PendingRow(item: HomeViewModel.Pending, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            when {
                item.info != null -> Text("▶", color = MaterialTheme.colorScheme.primary)
                item.failed -> Text("!", color = MaterialTheme.colorScheme.error)
                else -> CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            }
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            val info = item.info
            Text(
                info?.displayName ?: item.uri.lastPathSegment ?: "…",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
            )
            when {
                info != null -> {
                    val est = Format.estimateOutputBytes(
                        info.width, info.height, info.fps, info.codec, Quality.MID.id, info.durationMs,
                    )
                    Text(
                        "${info.width}×${info.height} · ${Format.duration(info.durationMs)} · " +
                            Format.bytes(info.sizeBytes) + " · " +
                            stringResource(R.string.home_est_output, Format.bytes(est)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                item.failed -> Text(
                    stringResource(R.string.error_probe_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                else -> Text(
                    stringResource(R.string.loading),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.home_remove))
        }
    }
}
