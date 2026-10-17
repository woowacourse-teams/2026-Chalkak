package com.stonefive.chalkak.feature.photoedit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stonefive.chalkak.domain.model.CropWindow
import com.stonefive.chalkak.domain.model.PhotoEdit
import com.stonefive.chalkak.domain.model.PhotoFilter
import kotlin.math.abs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class PhotoEditViewModel(
    imageUri: String,
    initialEdit: PhotoEdit,
) : ViewModel() {
    private var geometry: CropGeometry? = null
    private var straightenBaseCrop: CropWindow? = null
    private val _uiState = MutableStateFlow(PhotoEditUiState(imageUri = imageUri, edit = initialEdit))
    val uiState: StateFlow<PhotoEditUiState> = _uiState.asStateFlow()

    fun onImageLoaded(
        width: Int,
        height: Int,
    ) {
        if (geometry != null || width <= 0 || height <= 0) return
        val loaded = CropGeometry(width.toFloat(), height.toFloat())
        geometry = loaded
        _uiState.update {
            it.copy(
                imageSize = PhotoImageSize(width, height),
                edit = it.edit.copy(crop = loaded.constrain(it.edit)),
            )
        }
    }

    fun onTabSelect(tab: PhotoEditTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun onCropMove(
        deltaX: Float,
        deltaY: Float,
    ) {
        val geometry = geometry ?: return
        straightenBaseCrop = null
        _uiState.update { it.copy(edit = it.edit.copy(crop = geometry.move(it.edit, deltaX, deltaY))) }
    }

    fun onCropResize(
        corner: CropCorner,
        deltaX: Float,
        deltaY: Float,
    ) {
        val geometry = geometry ?: return
        straightenBaseCrop = null
        _uiState.update {
            val crop = geometry.resize(it.edit, corner, deltaX, deltaY, it.lockedAspectRatio)
            it.copy(edit = it.edit.copy(crop = crop))
        }
    }

    fun onStraightenChange(degrees: Float) {
        val geometry = geometry ?: return
        val baseCrop = straightenBaseCrop ?: _uiState.value.edit.crop
        straightenBaseCrop = baseCrop
        _uiState.update { it.copy(edit = geometry.straighten(it.edit, baseCrop, degrees)) }
    }

    fun onRotateClick() {
        val geometry = geometry ?: return
        straightenBaseCrop = null
        _uiState.update {
            it.copy(
                edit = geometry.rotateQuarter(it.edit),
                lockedAspectRatio = it.lockedAspectRatio?.let { ratio -> 1f / ratio },
            )
        }
    }

    fun onAspectRatioSelect(aspectRatio: CropAspectRatio) {
        val geometry = geometry ?: return
        straightenBaseCrop = null
        _uiState.update {
            val lockedAspectRatio = when (aspectRatio) {
                CropAspectRatio.Free -> null
                CropAspectRatio.Original -> geometry.turnedAspectRatio(it.edit.quarterTurns)
                else -> aspectRatio.ratio
            }
            val crop = if (lockedAspectRatio == null) {
                it.edit.crop
            } else {
                geometry.largestCrop(it.edit, lockedAspectRatio)
            }
            it.copy(
                edit = it.edit.copy(crop = crop),
                aspectRatio = aspectRatio,
                lockedAspectRatio = lockedAspectRatio,
            )
        }
    }

    fun onResetClick() {
        straightenBaseCrop = null
        _uiState.update {
            it.copy(
                edit = PhotoEdit(filter = it.edit.filter),
                aspectRatio = CropAspectRatio.Free,
                lockedAspectRatio = null,
            )
        }
    }

    fun onFilterSelect(filter: PhotoFilter) {
        _uiState.update { it.copy(edit = it.edit.copy(filter = filter)) }
    }

    fun onDoneClick() {
        _uiState.update { it.copy(completedEdit = it.edit.normalized()) }
    }

    // 끌기와 수평 보정을 되돌리며 생긴 부동소수점 오차 때문에 편집하지 않은 사진이 다시 인코딩되지 않게 한다.
    private fun PhotoEdit.normalized(): PhotoEdit = copy(
        straightenDegrees = if (abs(straightenDegrees) < NORMALIZE_EPSILON) 0f else straightenDegrees,
        crop = if (crop.isCloseTo(CropWindow.Full)) CropWindow.Full else crop,
    )

    private fun CropWindow.isCloseTo(other: CropWindow): Boolean = abs(centerX - other.centerX) < NORMALIZE_EPSILON &&
        abs(centerY - other.centerY) < NORMALIZE_EPSILON &&
        abs(width - other.width) < NORMALIZE_EPSILON &&
        abs(height - other.height) < NORMALIZE_EPSILON

    companion object {
        fun factory(
            imageUri: String,
            initialEdit: PhotoEdit,
        ) = viewModelFactory {
            initializer {
                PhotoEditViewModel(imageUri = imageUri, initialEdit = initialEdit)
            }
        }

        private const val NORMALIZE_EPSILON = 1e-3f
    }
}
