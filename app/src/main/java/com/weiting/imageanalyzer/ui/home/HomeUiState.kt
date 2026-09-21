package com.weiting.imageanalyzer.ui.home

import com.weiting.imageanalyzer.data.AnalyzableImage
import com.weiting.imageanalyzer.data.ShareCheckStatus

data class HomeUiState(
    val image: ImageState = ImageState.Empty,
    val shareCheck: ShareCheckStatus = ShareCheckStatus.Idle,
)

sealed interface ImageState {
    data object Empty : ImageState

    data object Loading : ImageState

    data class Ready(val image: AnalyzableImage) : ImageState

    data object Failed : ImageState
}
