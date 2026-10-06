package com.videodelite.app.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.videodelite.app.network.ApiException
import com.videodelite.app.R
import java.io.IOException

/** Grouped settings-style card (iOS inset grouped list look). */
@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
            .padding(vertical = 4.dp),
        content = content,
    )
}

/** Small grey section caption above a card. */
@Composable
fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 18.dp, bottom = 6.dp),
    )
}

/** iOS-style single-choice segmented control with a per-option sublabel. */
@Composable
fun <T> VdSegmented(
    options: List<T>,
    selected: T,
    label: @Composable ((T) -> String),
    sublabel: (@Composable ((T) -> String))? = null,
    enabled: (T) -> Boolean = { true },
    onSelect: (T) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                enabled = enabled(option),
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                label = {
                    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                        Text(label(option), maxLines = 1)
                        if (sublabel != null) {
                            Text(
                                sublabel(option),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                },
            )
        }
    }
}

/** Maps transport/API failures to localized, user-facing strings. */
fun Throwable.uiMessage(context: Context): String = when (this) {
    is ApiException -> if (code == 0) {
        context.getString(R.string.error_network)
    } else {
        message ?: context.getString(R.string.error_server, code)
    }
    is IOException -> context.getString(R.string.error_network)
    else -> message ?: context.getString(R.string.error_unknown)
}
