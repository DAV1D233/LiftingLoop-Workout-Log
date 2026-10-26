package com.fitlog.app.feedback

import android.media.*
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import com.fitlog.app.data.Preferences
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

enum class FeedbackCue { HOLD, SELECT, CONFIRM, SET, EXERCISE, REST, FINISH, ERROR }

class FeedbackPlayer : AutoCloseable {
    private val audio = ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(4), ThreadPoolExecutor.DiscardOldestPolicy())
    private data class Note(val hz: Double, val ms: Int, val gain: Double)
    fun play(cue: FeedbackCue, view: View, preferences: Preferences) {
        if (preferences.haptics) {
            val confirm = if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
            view.performHapticFeedback(when (cue) {
                FeedbackCue.HOLD -> HapticFeedbackConstants.LONG_PRESS
                FeedbackCue.SELECT -> HapticFeedbackConstants.CLOCK_TICK
                FeedbackCue.ERROR -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
                else -> confirm
            })
            if (cue == FeedbackCue.EXERCISE || cue == FeedbackCue.FINISH) view.postDelayed({
                if (view.isAttachedToWindow) view.performHapticFeedback(confirm)
            }, 110)
            if (cue == FeedbackCue.FINISH) view.postDelayed({
                if (view.isAttachedToWindow) view.performHapticFeedback(confirm)
            }, 250)
        }
        if (!preferences.sounds || audio.isShutdown) return
        val notes = when (cue) {
            FeedbackCue.HOLD -> listOf(Note(780.0, 40, .08))
            FeedbackCue.SELECT -> listOf(Note(1300.0, 24, .07))
            FeedbackCue.ERROR -> listOf(Note(280.0, 70, .10))
            FeedbackCue.CONFIRM -> listOf(Note(880.0, 45, .12), Note(1320.0, 65, .13))
            FeedbackCue.SET -> listOf(Note(660.0, 55, .16), Note(990.0, 90, .18))
            FeedbackCue.EXERCISE -> listOf(Note(660.0, 65, .17), Note(990.0, 80, .18), Note(1320.0, 110, .18))
            FeedbackCue.REST -> listOf(Note(880.0, 80, .12), Note(1175.0, 110, .13))
            FeedbackCue.FINISH -> listOf(Note(660.0, 90, .18), Note(880.0, 100, .20), Note(1320.0, 200, .22))
        }
        audio.execute { runCatching { synthesize(notes) } }
    }
    private fun synthesize(notes: List<Note>) {
        val rate = 22050
        val samples = ShortArray(notes.sumOf { rate * it.ms / 1000 })
        var cursor = 0
        for (note in notes) {
            val count = rate * note.ms / 1000
            repeat(count) { i ->
                val t = i.toDouble() / rate
                val attack = (i / (rate * .004)).coerceAtMost(1.0)
                val release = ((count - i) / (rate * .008)).coerceAtMost(1.0)
                val wave = sin(2 * PI * note.hz * t) + .12 * sin(4 * PI * note.hz * t)
                samples[cursor++] = (wave * attack * release * exp(-3.0 * i / count) * note.gain * Short.MAX_VALUE).toInt().toShort()
            }
        }
        val track = AudioTrack(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
            AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build(), samples.size * 2,
            AudioTrack.MODE_STATIC, AudioManager.AUDIO_SESSION_ID_GENERATE)
        try { track.write(samples, 0, samples.size); track.play(); Thread.sleep(notes.sumOf { it.ms }.toLong() + 60) }
        finally { track.release() }
    }
    override fun close() { audio.shutdownNow() }
}
