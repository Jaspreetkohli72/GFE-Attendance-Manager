package com.jaspreet.gfeattendancemanager.util

import android.media.AudioManager
import android.media.ToneGenerator

class SoundManager {

    private var toneGenerator: ToneGenerator? = null

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        } catch (e: Exception) {
            KioskLogger.error("Failed to initialize ToneGenerator", e)
            toneGenerator = null
        }
    }

    fun playSuccessTone() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
        } catch (e: Exception) {
            KioskLogger.error("Failed to play success tone", e)
        }
    }

    fun playErrorTone() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_NACK, 250)
        } catch (e: Exception) {
            KioskLogger.error("Failed to play error tone", e)
        }
    }

    fun release() {
        try {
            toneGenerator?.release()
        } catch (e: Exception) {
            KioskLogger.error("Failed to release ToneGenerator", e)
        } finally {
            toneGenerator = null
        }
    }
}
