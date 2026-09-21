package com.weiting.imageanalyzer.ui.home

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.weiting.imageanalyzer.R
import com.weiting.imageanalyzer.data.ImageDetails
import com.weiting.imageanalyzer.data.ShareCheckStatus
import com.weiting.imageanalyzer.ui.theme.ImageAnalyzerTheme
import org.koin.androidx.compose.koinViewModel

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) viewModel.onImagePicked(uri) }

    HomeContent(
        state = uiState,
        onPickImage = {
            pickImage.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        },
        onClearImage = viewModel::onImageCleared,
        onRunShareCheck = viewModel::onRunShareCheck,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(
    state: HomeUiState,
    onPickImage: () -> Unit,
    onClearImage: () -> Unit,
    onRunShareCheck: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
            when (val image = state.image) {
                ImageState.Empty -> EmptyImageSlot(onPick = onPickImage)

                ImageState.Loading -> ImageSlotFrame {
                    CircularProgressIndicator()
                }

                ImageState.Failed -> ImageSlotFrame {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.image_load_failed),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = onPickImage) {
                            Text(stringResource(R.string.pick_another_image))
                        }
                    }
                }

                is ImageState.Ready -> {
                    ImagePreview(image)
                    Spacer(Modifier.height(20.dp))
                    ImageDetailsList(details = image.image.details)
                    Spacer(Modifier.height(20.dp))
                    ShareCheckSection(status = state.shareCheck, onRun = onRunShareCheck)
                    Spacer(Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilledTonalButton(
                            onClick = onPickImage,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.pick_another_image))
                        }
                        OutlinedButton(
                            onClick = onClearImage,
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
private fun ImagePreview(image: ImageState.Ready) {
    val bitmap = image.image.bitmap
    // Converting is cheap but not free, and the bitmap outlives recomposition.
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Image(
            bitmap = imageBitmap,
            contentDescription = stringResource(R.string.selected_image_description),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(imageBitmap.width.toFloat() / imageBitmap.height)
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

/** Runs the on-device share-suitability check and shows whatever the model came back with. */
@Composable
private fun ShareCheckSection(status: ShareCheckStatus, onRun: () -> Unit) {
    val context = LocalContext.current
    HorizontalDivider()
    Spacer(Modifier.height(20.dp))

    when (status) {
        ShareCheckStatus.Idle -> Button(
            onClick = onRun,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.run_share_check))
        }

        ShareCheckStatus.Preparing -> ProgressRow(stringResource(R.string.share_check_preparing))

        is ShareCheckStatus.Downloading -> Column {
            Text(
                text = stringResource(R.string.share_check_downloading),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            if (status.totalBytes > 0) {
                LinearProgressIndicator(
                    progress = { status.downloadedBytes.toFloat() / status.totalBytes },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = Formatter.formatShortFileSize(context, status.downloadedBytes) +
                        " / " + Formatter.formatShortFileSize(context, status.totalBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }

        ShareCheckStatus.Analyzing -> ProgressRow(stringResource(R.string.share_check_analyzing))

        is ShareCheckStatus.Done -> Column {
            Text(
                text = stringResource(R.string.share_check_result),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = status.answer,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.share_check_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onRun, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.share_check_rerun))
            }
        }

        ShareCheckStatus.Unsupported -> Text(
            text = stringResource(R.string.share_check_unsupported),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )

        is ShareCheckStatus.Failed -> Column {
            Text(
                text = stringResource(R.string.share_check_failed, status.message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onRun, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.share_check_rerun))
            }
        }
    }
}

@Composable
private fun ProgressRow(label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.height(20.dp).aspectRatio(1f))
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
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

// Previews the stateless half, so no Koin graph is needed.
@Preview(showBackground = true)
@Composable
private fun HomeContentPreview() {
    ImageAnalyzerTheme {
        HomeContent(
            state = HomeUiState(),
            onPickImage = {},
            onClearImage = {},
            onRunShareCheck = {},
        )
    }
}
