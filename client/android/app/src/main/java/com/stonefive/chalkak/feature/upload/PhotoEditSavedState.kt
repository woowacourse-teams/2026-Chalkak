package com.stonefive.chalkak.feature.upload

import com.stonefive.chalkak.domain.model.photoedit.CropWindow
import com.stonefive.chalkak.domain.model.photoedit.PhotoAdjustment
import com.stonefive.chalkak.domain.model.photoedit.PhotoAdjustmentLayers
import com.stonefive.chalkak.domain.model.photoedit.PhotoAdjustments
import com.stonefive.chalkak.domain.model.photoedit.PhotoEdit
import com.stonefive.chalkak.domain.model.photoedit.PhotoFilter

/** 이미지 대신 확정된 편집 파라미터만 저장한다. 이름을 써서 enum의 선언 순서에 의존하지 않는다. */
internal fun PhotoEdit.toSavedState(): HashMap<String, String> = hashMapOf(
    "quarterTurns" to quarterTurns.toString(),
    "straightenDegrees" to straightenDegrees.toString(),
    "cropCenterX" to crop.centerX.toString(),
    "cropCenterY" to crop.centerY.toString(),
    "cropWidth" to crop.width.toString(),
    "cropHeight" to crop.height.toString(),
    "filter" to filter.name,
    "intensity" to adjustmentLayers.intensity.toString(),
).apply {
    PhotoAdjustment.entries.forEach { adjustment ->
        adjustmentLayers.automatic?.let { put("automatic.${adjustment.name}", it[adjustment].toString()) }
        adjustmentLayers.manual[adjustment]?.let { put("manual.${adjustment.name}", it.toString()) }
    }
}

internal fun Map<String, String>.toPhotoEdit(): PhotoEdit = PhotoEdit(
    quarterTurns = getValue("quarterTurns").toInt(),
    straightenDegrees = getValue("straightenDegrees").toFloat(),
    crop = CropWindow(
        centerX = getValue("cropCenterX").toFloat(),
        centerY = getValue("cropCenterY").toFloat(),
        width = getValue("cropWidth").toFloat(),
        height = getValue("cropHeight").toFloat(),
    ),
    filter = PhotoFilter.valueOf(getValue("filter")),
    adjustmentLayers = PhotoAdjustmentLayers(
        automatic = if (containsKey("automatic.${PhotoAdjustment.Exposure.name}")) {
            PhotoAdjustments.of { getValue("automatic.${it.name}").toFloat() }
        } else {
            null
        },
        intensity = getValue("intensity").toFloat(),
        manual = PhotoAdjustment.entries
            .mapNotNull { adjustment ->
                get("manual.${adjustment.name}")?.toFloat()?.let { adjustment to it }
            }.toMap(),
    ),
)
