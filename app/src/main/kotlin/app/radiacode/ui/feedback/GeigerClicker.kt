package app.radiacode.ui.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import app.radiacode.ui.logic.ClickEngine
import app.radiacode.ui.logic.ClickRate
import app.radiacode.ui.logic.ClickWaveform
import app.radiacode.ui.logic.EnergyTone
import kotlin.random.Random

/**
 * Geiger-style click synthesizer for the Search screen. A streaming
 * [AudioTrack] renders silence with programmatically generated PCM «ticks»
 * (no bundled audio assets) at Poisson-random intervals whose mean follows
 * the live CPS ([ClickRate]); the frame-by-frame state machine itself is the
 * pure [ClickEngine], unit-tested on the JVM.
 *
 * Foreground-only by design: the owner starts/stops it with the screen
 * lifecycle.
 *
 * **Audio routing.** The clicks play with `USAGE_MEDIA`, i.e. on the music
 * stream. They used to use `USAGE_ASSISTANCE_SONIFICATION`, which Android
 * maps to `STREAM_SYSTEM` — a stream that is muted outright whenever the
 * ringer is in vibrate or silent mode and whose level follows the ring
 * volume slider. On a phone kept on vibrate that produced exactly what the
 * field report describes: the mode is on, the engine runs, and nothing is
 * audible with nothing on screen to explain it. These clicks are explicit,
 * user-initiated, continuous feedback, so the media stream — the slider the
 * user reaches for — is the honest carrier.
 *
 * **Audio focus.** We ask for transient-may-duck focus to be polite, but a
 * *denied* request no longer silences us: denial is not the user's decision
 * and would lock the feature off permanently with no way back. We do go
 * quiet while focus is actively lost after having been granted.
 *
 * Do-Not-Disturb still silences the clicks (they are convenience feedback,
 * not an alarm); [dndBlocked] surfaces that so the screen can say so.
 */
class GeigerClicker(context: Context) {

    private val appContext = context.applicationContext
    private val audioManager =
        appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    @Volatile
    private var rate = 0f

    @Volatile
    private var running = false

    /** Focus was granted and then actively lost — the only case we mute for. */
    @Volatile
    private var focusLost = false

    /** The request was denied outright; we keep playing, but say so. */
    @Volatile
    var focusDenied = false
        private set

    /** The audio engine could not be started at all. Surfaced on screen. */
    @Volatile
    var audioUnavailable = false
        private set

    /** Do-Not-Disturb is silencing the clicks right now. */
    @Volatile
    var dndBlocked = false
        private set

    private var thread: Thread? = null
    private var focusRequest: AudioFocusRequest? = null

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    @Volatile
    private var toneBand: EnergyTone.Band? = null

    /** Media volume is at zero — the engine is fine, the slider is not. */
    val volumeZero: Boolean
        get() = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) == 0

    /** Clicks per second; thread-safe, applied within one render chunk. */
    fun setRate(clicksPerSecond: Float) {
        rate = clicksPerSecond
    }

    /**
     * «Тон по энергии»: pitch band for upcoming clicks, null = the default
     * tick (no fresh spectrum data or the mode is off). Applied at the next
     * click start — a click already sounding keeps its pitch.
     */
    fun setToneBand(band: EnergyTone.Band?) {
        toneBand = band
    }

    fun start() {
        if (running) return
        running = true
        focusLost = false
        audioUnavailable = false

        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener { change ->
                when (change) {
                    AudioManager.AUDIOFOCUS_LOSS,
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                    -> focusLost = true
                    AudioManager.AUDIOFOCUS_GAIN -> {
                        focusLost = false
                        focusDenied = false
                    }
                    else -> Unit // ducking: keep clicking, quieter
                }
            }
            .build()
        focusRequest = request
        focusDenied =
            audioManager.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED

        thread = Thread(::renderLoop, "geiger-clicker").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        if (!running) return
        running = false
        thread?.join(1_000)
        thread = null
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    private fun renderLoop() {
        val defaultClick = ClickWaveform.pcm16(SAMPLE_RATE)
        val bandClicks = EnergyTone.Band.entries.associateWith { band ->
            ClickWaveform.pcm16(SAMPLE_RATE, EnergyTone.frequencyHz(band))
        }
        val track = runCatching { createTrack() }.getOrNull()
        if (track == null || track.state != AudioTrack.STATE_INITIALIZED) {
            // Honest failure: no audio channel. The screen and the self-test
            // in Настройки → Проверка report this instead of staying mute.
            audioUnavailable = true
            track?.release()
            return
        }
        try {
            track.play()
            val engine = ClickEngine(SAMPLE_RATE) { Random.nextFloat() }
            val chunk = ShortArray(CHUNK_FRAMES)
            var lastDndCheckAt = 0L

            while (running) {
                val now = System.currentTimeMillis()
                if (now - lastDndCheckAt >= DND_CHECK_MILLIS) {
                    lastDndCheckAt = now
                    dndBlocked = !Feedback.dndAllowsFeedback(appContext)
                }
                val currentRate = if (focusLost || dndBlocked) 0f else rate
                val click = toneBand?.let { bandClicks[it] } ?: defaultClick
                engine.fillChunk(chunk, currentRate, click)
                // Blocking write paces the loop at real time.
                track.write(chunk, 0, chunk.size)
            }
        } catch (error: IllegalStateException) {
            audioUnavailable = true
        } finally {
            runCatching { track.stop() }
            track.release()
        }
    }

    private fun createTrack(): AudioTrack {
        val minBuffer = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        // Buffer size is in BYTES; a chunk is CHUNK_FRAMES *shorts*, i.e.
        // twice as many bytes. Four chunks of headroom keeps the blocking
        // write pacing smooth without adding audible latency (~185 ms).
        val wanted = CHUNK_FRAMES * BYTES_PER_FRAME * 4
        return AudioTrack(
            attributes,
            AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build(),
            maxOf(minBuffer, wanted),
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE,
        )
    }

    companion object {
        private const val SAMPLE_RATE = 44_100
        private const val BYTES_PER_FRAME = 2

        /** ~46 ms per chunk: rate changes and DND/focus apply promptly. */
        private const val CHUNK_FRAMES = 2_048
        private const val DND_CHECK_MILLIS = 1_000L
    }
}
