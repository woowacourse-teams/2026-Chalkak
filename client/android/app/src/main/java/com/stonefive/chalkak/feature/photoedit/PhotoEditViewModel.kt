package com.stonefive.chalkak.feature.photoedit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stonefive.chalkak.ChalkakApplication
import com.stonefive.chalkak.domain.model.photoedit.CropWindow
import com.stonefive.chalkak.domain.model.photoedit.PhotoAdjustmentLayers
import com.stonefive.chalkak.domain.model.photoedit.PhotoAutoAdjuster
import com.stonefive.chalkak.domain.model.photoedit.PhotoEdit
import com.stonefive.chalkak.domain.model.photoedit.PhotoFilter
import com.stonefive.chalkak.domain.model.photoedit.PhotoImageStatistics
import com.stonefive.chalkak.domain.model.photoedit.PhotoPixels
import com.stonefive.chalkak.domain.repository.PhotoEditImageRepository
import kotlin.math.abs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class PhotoEditViewModel(
    imageUri: String,
    initialEdit: PhotoEdit,
    private val imageRepository: PhotoEditImageRepository,
    private val analysisDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private var geometry: CropGeometry? = null
    private val _uiState = MutableStateFlow(PhotoEditUiState(imageUri = imageUri, edit = initialEdit))
    val uiState: StateFlow<PhotoEditUiState> = _uiState.asStateFlow()

    /** 자동 보정값을 구할 때 쓰는 사진. 불러오지 못하면 자동 보정은 아무것도 바꾸지 않는다. */
    private val analysisSource = CompletableDeferred<PhotoPixels?>()
    private val analysisMutex = Mutex()

    /** 자동 보정값을 구한 회전·자르기. 바뀌면 다시 분석한다. */
    private var analyzedGeometry: PhotoEdit? =
        initialEdit.geometry().takeIf { initialEdit.adjustmentLayers.automatic != null }

    init {
        viewModelScope.launch {
            analysisSource.complete(imageRepository.loadPixels(imageUri, ANALYSIS_SOURCE_LONG_EDGE))
            refreshAutoAdjustments()
        }
    }

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

    // 자르기를 마치고 다른 탭으로 가면 자른 사진으로 자동 보정값을 다시 구한다.
    fun onTabSelect(tab: PhotoEditTab) {
        _uiState.update { it.copy(selectedTab = tab) }
        viewModelScope.launch { refreshAutoAdjustments() }
    }

    fun onCropMove(
        deltaX: Float,
        deltaY: Float,
    ) {
        val geometry = geometry ?: return
        _uiState.update { it.copy(edit = it.edit.copy(crop = geometry.move(it.edit, deltaX, deltaY))) }
    }

    fun onCropResize(
        corner: CropCorner,
        deltaX: Float,
        deltaY: Float,
    ) {
        val geometry = geometry ?: return
        _uiState.update {
            val crop = geometry.resize(it.edit, corner, deltaX, deltaY, it.lockedAspectRatio)
            it.copy(edit = it.edit.copy(crop = crop))
        }
    }

    // 원본 비율이나 사진 전체를 덮는 자유 프레임은 사진 모양 자체라 사진과 함께 돈다. 그 밖의 프레임은 모양을 유지한다.
    fun onRotateClick() {
        val geometry = geometry ?: return
        _uiState.update {
            val crop = it.edit.crop
            val coversWholeImage = crop.isCloseTo(CropWindow.Full)
            val followsImage = it.aspectRatio == CropAspectRatio.Original ||
                (it.aspectRatio == CropAspectRatio.Free && coversWholeImage)
            if (followsImage) {
                it.copy(
                    edit = geometry.rotateQuarter(it.edit),
                    lockedAspectRatio = it.lockedAspectRatio?.let { ratio -> 1f / ratio },
                )
            } else {
                // 고정 비율은 돌린 사진에서 다시 가장 크게 맞춰, 비율 버튼을 다시 누른 것과 같게 한다.
                val rotated = geometry.rotateQuarterKeepingFrame(it.edit)
                val rotatedCrop = it.lockedAspectRatio?.let { ratio -> geometry.largestCrop(rotated, ratio) }
                it.copy(edit = rotatedCrop?.let { crop -> rotated.copy(crop = crop) } ?: rotated)
            }
        }
    }

    fun onAspectRatioSelect(aspectRatio: CropAspectRatio) {
        val geometry = geometry ?: return
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
        _uiState.update {
            it.copy(
                edit = PhotoEdit(filter = it.edit.filter, adjustmentLayers = it.edit.adjustmentLayers),
                aspectRatio = CropAspectRatio.Free,
                lockedAspectRatio = null,
            )
        }
    }

    /** 자동 보정은 처음 고를 때 켜고, 고른 상태에서 다시 누르면 껐다 켠다. */
    fun onAdjustItemSelect(item: PhotoAdjustItem) {
        val state = _uiState.value
        _uiState.update { it.copy(selectedAdjustItem = item) }
        if (item != PhotoAdjustItem.Auto) return
        val intensity = state.edit.adjustmentLayers.intensity
        when {
            state.selectedAdjustItem == item -> setValue(item, if (intensity > 0f) 0f else 1f)
            intensity == 0f -> setValue(item, 1f)
        }
    }

    fun onAdjustmentChange(value: Float) {
        setValue(_uiState.value.selectedAdjustItem, value)
    }

    fun onAdjustmentsResetClick() {
        updateAdjustmentLayers { it.cleared() }
    }

    fun onFilterSelect(filter: PhotoFilter) {
        _uiState.update { it.copy(edit = it.edit.copy(filter = filter)) }
    }

    // 자동 보정을 켠 채 자르기를 바꿨다면 바뀐 사진으로 다시 분석한 값으로 완료한다.
    fun onDoneClick() {
        viewModelScope.launch {
            if (_uiState.value.edit.adjustmentLayers.intensity > 0f) refreshAutoAdjustments()
            _uiState.update { it.copy(completedEdit = it.edit.normalized()) }
        }
    }

    private fun setValue(
        item: PhotoAdjustItem,
        value: Float,
    ) {
        val clamped = value.coerceIn(item.range)
        updateAdjustmentLayers { layers ->
            when (item) {
                PhotoAdjustItem.Auto -> layers.withIntensity(clamped)
                is PhotoAdjustItem.Manual -> layers.withValue(item.adjustment, clamped)
            }
        }
    }

    private fun updateAdjustmentLayers(transform: (PhotoAdjustmentLayers) -> PhotoAdjustmentLayers) {
        _uiState.update { it.copy(edit = it.edit.copy(adjustmentLayers = transform(it.edit.adjustmentLayers))) }
    }

    private suspend fun refreshAutoAdjustments() = analysisMutex.withLock {
        val source = analysisSource.await() ?: return@withLock
        val geometryEdit = _uiState.value.edit
            .geometry()
        if (geometryEdit == analyzedGeometry) return@withLock
        analyzedGeometry = geometryEdit
        val automatic = withContext(analysisDispatcher) {
            PhotoAutoAdjuster.adjustments(
                PhotoImageStatistics.of(source.sampled(geometryEdit, PhotoImageStatistics.SAMPLE_LONG_EDGE)),
            )
        }
        updateAdjustmentLayers { it.withAutomatic(automatic) }
    }

    private fun PhotoEdit.geometry(): PhotoEdit =
        PhotoEdit(quarterTurns = quarterTurns, straightenDegrees = straightenDegrees, crop = crop)

    // 끌기를 되돌리며 생긴 부동소수점 오차 때문에 편집하지 않은 사진이 다시 인코딩되지 않게 한다.
    // 자동 보정을 껐다면 그 값은 결과에 영향이 없으므로 보관하지 않는다.
    private fun PhotoEdit.normalized(): PhotoEdit = copy(
        crop = if (crop.isCloseTo(CropWindow.Full)) CropWindow.Full else crop,
        adjustmentLayers = adjustmentLayers.takeIf { it.intensity > 0f } ?: adjustmentLayers.copy(automatic = null),
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
                val application = this[APPLICATION_KEY] as ChalkakApplication
                PhotoEditViewModel(
                    imageUri = imageUri,
                    initialEdit = initialEdit,
                    imageRepository = application.appContainer.photoEditImageRepository,
                )
            }
        }

        private const val NORMALIZE_EPSILON = 1e-3f

        // 작게 자른 영역도 분석용 크기(긴 변 256px)를 채울 만큼 크게 읽는다.
        private const val ANALYSIS_SOURCE_LONG_EDGE = 1024
    }
}
