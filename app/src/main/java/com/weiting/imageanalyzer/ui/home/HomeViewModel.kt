package com.weiting.imageanalyzer.ui.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.weiting.imageanalyzer.data.ImageRepository
import com.weiting.imageanalyzer.data.ShareCheckRepository
import com.weiting.imageanalyzer.data.ShareCheckStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

class HomeViewModel(
    private val imageRepository: ImageRepository,
    private val shareCheckRepository: ShareCheckRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /** Tracked so picking a new image cancels a check still running against the old one. */
    private var checkJob: Job? = null

    fun onImagePicked(uri: Uri) {
        checkJob?.cancel()
        _uiState.value = HomeUiState(image = ImageState.Loading)

        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    image = runCatching { imageRepository.load(uri) }
                        .fold(
                            onSuccess = { ImageState.Ready(it) },
                            onFailure = { e ->
                                Timber.e(e, "failed to decode %s", uri)
                                ImageState.Failed
                            },
                        ),
                )
            }
        }
    }

    fun onImageCleared() {
        checkJob?.cancel()
        _uiState.value = HomeUiState()
    }

    fun onRunShareCheck() {
        val ready = _uiState.value.image as? ImageState.Ready ?: return
        checkJob?.cancel()
        // viewModelScope, not the composition: a rotation must not throw away a running inference.
        checkJob = viewModelScope.launch {
            shareCheckRepository.check(ready.image.bitmap).collect { status ->
                _uiState.update { it.copy(shareCheck = status) }
            }
        }
    }
}
