package dev.huecoder.md3agent.data.settings

import kotlinx.serialization.Serializable

@Serializable
data class ChatSettings(
    val showTokenUsage: Boolean = true,
    val autoTitle: Boolean = true,
    val autoCompact: Boolean = true,
    /** Share of the model's context window at which a chat is compacted before the next reply. */
    val compactAt: Float = 0.85f,
    val typing: TypingStyle = TypingStyle(),
    val cameraFrame: CameraFrame = CameraFrame.FULL_SCREEN,
)

/** Shape of photos taken with the in-app camera: what fills the screen, or a fixed 16:9 / 4:3 frame. */
@Serializable
enum class CameraFrame { FULL_SCREEN, RATIO_16_9, RATIO_4_3 }

@Serializable
enum class TypingMode { FADE, TYPEWRITER, RAW }

@Serializable
data class TypingStyle(
    val mode: TypingMode = TypingMode.FADE,
    val charsPerSecond: Int = DEFAULT_CHARS_PER_SECOND,
) {
    val isAnimated: Boolean get() = mode != TypingMode.RAW

    companion object {
        const val MIN_CHARS_PER_SECOND = 30
        const val MAX_CHARS_PER_SECOND = 300
        const val DEFAULT_CHARS_PER_SECOND = 90
    }
}
