package com.sam2kb.lyriqprobe.media

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class MediaForegroundOutcome(
    val started: Boolean,
    val stopped: Boolean,
    val notificationPermissionGranted: Boolean,
    val notificationsEnabled: Boolean,
    val notificationChannelCreated: Boolean,
    val error: String?,
)

object MediaProbeBroker {
    private val mutableEvents = MutableSharedFlow<MediaForegroundOutcome>(extraBufferCapacity = 4)
    val events = mutableEvents.asSharedFlow()

    fun publish(outcome: MediaForegroundOutcome) {
        mutableEvents.tryEmit(outcome)
    }
}
