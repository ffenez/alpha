package app.radiacode.ui.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import app.radiacode.ui.logic.ClickWaveform
import app.radiacode.ui.logic.SoundCheck
import app.radiacode.ui.logic.VibrationCheck

/**
 * Настройки → Проверка: «does the feedback engine work at all».
 *
 * The Search screen gates clicks behind a live device, fresh samples, audio
 * focus, DND and a recorded background — so when a user reports «no sound»,
 * we cannot tell a broken engine from wrong wiring. These two checks bypass
 * every gate: they render straight to an [AudioTrack] and straight to the
 * vibrator, then report exactly what the system said. The only thing they
 * cannot override is the physical mute/volume of the device, which is why
 * the result line names that case explicitly.
 */
object FeedbackSelfTest {

    private const val SAMPLE_RATE = 44_100
    private const val CLICKS = 5
    private const val CLICK_INTERVAL_SECONDS = 0.2f

    /** Plays [CLICKS] clicks at a fixed rate and reports what happened. */
    fun playClicks(context: Context): SoundCheck {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val focusRequest = AudioFocusRequest.Builder(
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
        ).setAudioAttributes(attributes).build()
        val focusGranted = audioManager.requestAudioFocus(focusRequest) ==
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED

        val pcm = renderClicks()
        val bytes = pcm.size * 2
        val track = runCatching {
            AudioTrack(
                attributes,
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build(),
                bytes,
                AudioTrack.MODE_STATIC,
                AudioManager.AUDIO_SESSION_ID_GENERATE,
            )
        }.getOrNull()

        val initialized = track != null && track.state == AudioTrack.STATE_INITIALIZED
        if (initialized) {
            requireNotNull(track).write(pcm, 0, pcm.size)
            runCatching { track.play() }
            // Release once the buffer has played out; the check itself is
            // synchronous from the user's point of view.
            Handler(Looper.getMainLooper()).postDelayed(
                {
                    runCatching { track.stop() }
                    track.release()
                    audioManager.abandonAudioFocusRequest(focusRequest)
                },
                (pcm.size * 1000L / SAMPLE_RATE) + 200L,
            )
        } else {
            track?.release()
            audioManager.abandonAudioFocusRequest(focusRequest)
        }

        return SoundCheck(
            trackInitialized = initialized,
            focusGranted = focusGranted,
            dndBlocked = !Feedback.dndAllowsFeedback(context),
            volumeZero = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) == 0,
        )
    }

    /** One pulse, ignoring the DND gate the Search screen applies. */
    fun pulse(context: Context): VibrationCheck {
        val emitted = Feedback.pulseNow(context)
        return VibrationCheck(
            hasVibrator = emitted || Feedback.hasVibrator(context),
            dndBlocked = !Feedback.dndAllowsFeedback(context),
        )
    }

    /** Fixed-rate click train — no Poisson randomness, this is a probe. */
    private fun renderClicks(): ShortArray {
        val click = ClickWaveform.pcm16(SAMPLE_RATE)
        val step = (SAMPLE_RATE * CLICK_INTERVAL_SECONDS).toInt()
        val pcm = ShortArray(step * CLICKS)
        for (index in 0 until CLICKS) {
            click.copyInto(pcm, destinationOffset = index * step)
        }
        return pcm
    }
}
