package com.videodelite.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.videodelite.app.R
import com.videodelite.app.media.AudioRateControl
import com.videodelite.app.media.ProConfig
import com.videodelite.app.media.RateControl
import com.videodelite.app.media.VideoCodec
import com.videodelite.app.ui.components.SectionCard
import com.videodelite.app.ui.components.SectionLabel
import com.videodelite.app.ui.components.VdSegmented

/**
 * Professional-mode panel (desktop parity, minus the ffmpeg-only options).
 * Only reachable with a signed-in, activated account — the caller gates it.
 */
@Composable
fun ProPanel(
    cfg: ProConfig,
    hevcAvailable: Boolean,
    onChange: (ProConfig) -> Unit,
) {
    SectionLabel(stringResource(R.string.pro_video))
    SectionCard {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(stringResource(R.string.home_codec), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            VdSegmented(
                options = VideoCodec.entries.toList(),
                selected = cfg.codec,
                label = { it.name },
                enabled = { it != VideoCodec.H265 || hevcAvailable },
                onSelect = { onChange(cfg.copy(codec = it)) },
            )
        }

        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(stringResource(R.string.pro_rate_control), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            VdSegmented(
                options = RateControl.entries.toList(),
                selected = cfg.rateControl,
                label = {
                    when (it) {
                        RateControl.CBR -> "CBR"
                        RateControl.VBR -> "VBR"
                        RateControl.CQ -> stringResource(R.string.pro_rc_cq)
                    }
                },
                onSelect = { onChange(cfg.copy(rateControl = it)) },
            )
            Text(
                stringResource(R.string.pro_rc_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        if (cfg.rateControl == RateControl.CQ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.pro_quality),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${cfg.quality}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Slider(
                    value = cfg.quality.toFloat(),
                    onValueChange = { onChange(cfg.copy(quality = it.toInt())) },
                    valueRange = ProConfig.MIN_QUALITY.toFloat()..ProConfig.MAX_QUALITY.toFloat(),
                )
                Text(
                    stringResource(R.string.pro_quality_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.pro_bitrate),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = formatMbps(cfg.bitrateMbps),
                        onValueChange = { raw ->
                            parseMbps(raw)?.let { onChange(cfg.copy(bitrateMbps = it)) }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.width(130.dp),
                    )
                }
                val error = ProConfig.error(cfg)
                Text(
                    stringResource(
                        R.string.pro_bitrate_range,
                        ProConfig.MIN_BITRATE_MBPS,
                        ProConfig.MAX_BITRATE_MBPS,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (error == "bitrate") MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                if (cfg.rateControl == RateControl.VBR) {
                    Text(
                        stringResource(R.string.pro_vbr_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    SectionLabel(stringResource(R.string.pro_audio))
    SectionCard {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(stringResource(R.string.pro_rate_control), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            VdSegmented(
                options = AudioRateControl.entries.toList(),
                selected = cfg.audioRateControl,
                label = { it.id.uppercase() },
                onSelect = { onChange(cfg.copy(audioRateControl = it)) },
            )
        }
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.pro_audio_bitrate),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${cfg.audioBitrateKbps} kbps",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(8.dp))
            // Two rows over the desktop's 64–512 choice list. A value outside
            // a row's range uses -1 so that row shows no (false) selection.
            VdSegmented(
                options = ProConfig.AUDIO_BITRATE_CHOICES.take(5),
                selected = cfg.audioBitrateKbps.takeIf { it in 64..192 } ?: -1,
                label = { "$it" },
                onSelect = { onChange(cfg.copy(audioBitrateKbps = it)) },
            )
            Spacer(Modifier.height(8.dp))
            VdSegmented(
                options = ProConfig.AUDIO_BITRATE_CHOICES.drop(5),
                selected = cfg.audioBitrateKbps.takeIf { it in 224..512 } ?: -1,
                label = { "$it" },
                onSelect = { onChange(cfg.copy(audioBitrateKbps = it)) },
            )
        }
    }
}

private fun formatMbps(v: Double): String =
    if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()

private fun parseMbps(raw: String): Double? =
    raw.replace(',', '.').toDoubleOrNull()
