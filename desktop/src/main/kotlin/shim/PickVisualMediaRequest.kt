package androidx.activity.result

import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.activity.result.contract.DesktopFiles

class PickVisualMediaRequest(val mediaType: PickVisualMedia.VisualMediaType = PickVisualMedia.ImageAndVideo) {
    internal fun exts(): Set<String> = when (mediaType) {
        PickVisualMedia.ImageOnly -> DesktopFiles.images
        PickVisualMedia.VideoOnly -> DesktopFiles.videos
        else -> DesktopFiles.images + DesktopFiles.videos
    }
    internal fun title() = when (mediaType) {
        PickVisualMedia.ImageOnly -> "Выберите изображение"
        PickVisualMedia.VideoOnly -> "Выберите видео"
        else -> "Выберите фото или видео"
    }
}

