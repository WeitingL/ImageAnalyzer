package com.weiting.imageanalyzer.ui.home

import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.weiting.imageanalyzer.R
import com.weiting.imageanalyzer.ui.theme.ImageAnalyzerTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // Saveable so the pick survives rotation; the state below is re-derived from it.
    var selectedUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var state by remember { mutableStateOf<ImageUiState>(ImageUiState.Empty) }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) selectedUri = uri }

    LaunchedEffect(selectedUri) {
        val uri = selectedUri
        if (uri == null) {
            state = ImageUiState.Empty
            return@LaunchedEffect
        }
        state = ImageUiState.Loading
        state = loadImage(context, uri)
    }

    val launchPicker = {
        pickImage.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.home_title)) }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            when (val current = state) {
                ImageUiState.Empty -> EmptyImageSlot(onPick = launchPicker)

                ImageUiState.Loading -> ImageSlotFrame {
                    CircularProgressIndicator()
                }

                ImageUiState.Failed -> ImageSlotFrame {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.image_load_failed),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = launchPicker) {
                            Text(stringResource(R.string.pick_another_image))
                        }
                    }
                }

                is ImageUiState.Ready -> {
                    ImagePreview(state = current)
                    Spacer(Modifier.height(20.dp))
                    ImageDetailsList(details = current.details)
                    Spacer(Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilledTonalButton(
                            onClick = launchPicker,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.pick_another_image))
                        }
                        OutlinedButton(
                            onClick = { selectedUri = null },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.remove_image))
                        }
                    }
                }
            }
        }
    }
}

/** Dashed drop-zone shown until the user picks something. */
@Composable
private fun EmptyImageSlot(onPick: () -> Unit) {
    val outline = MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(3f / 4f)
            .drawBehind {
                drawRoundRect(
                    color = outline,
                    cornerRadius = CornerRadius(24.dp.toPx()),
                    style = Stroke(
                        width = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(14.dp.toPx(), 10.dp.toPx()),
                        ),
                    ),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Text(
                text = stringResource(R.string.empty_slot_title),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.empty_slot_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onPick) {
                Text(stringResource(R.string.pick_image))
            }
        }
    }
}

/** Same footprint as the drop zone, for the loading and error states. */
@Composable
private fun ImageSlotFrame(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(3f / 4f),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
private fun ImagePreview(state: ImageUiState.Ready) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Image(
            bitmap = state.bitmap,
            contentDescription = stringResource(R.string.selected_image_description),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(state.bitmap.width.toFloat() / state.bitmap.height)
                .clip(RoundedCornerShape(24.dp)),
        )
    }
}

@Composable
private fun ImageDetailsList(details: ImageDetails) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        details.displayName?.let {
            DetailRow(label = stringResource(R.string.detail_file_name), value = it)
        }
        DetailRow(
            label = stringResource(R.string.detail_dimensions),
            value = "${details.width} × ${details.height}",
        )
        details.sizeBytes?.let {
            DetailRow(
                label = stringResource(R.string.detail_file_size),
                value = Formatter.formatShortFileSize(context, it),
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    ImageAnalyzerTheme {
        HomeScreen()
    }
}
