package app.radiacode.ui.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import app.radiacode.ui.logic.ClickRate
import app.radiacode.ui.logic.ClickWaveform
import kotlin.random.Random

/**
 * Geiger-style click synthesizer for the Search screen. A streaming
 * [AudioTrack] renders silence with programmatically generated PCM «ticks»
 * (no bundled audio assets) at Poisson-random intervals whose mean follows
 * the live CPS ([ClickRate]).
 *
 * Foreground-only by design: the owner starts/stops it with the screen
 * lifecycle. Politeness rules:
 *  - USAGE_ASSISTANCE_SONIFICATION audio attributes;
 *  - transient-may-duck audio focus, silenced while focus is lost;
 *  - silenced whenever Do-Not-Disturb is active (any filter except «all»).
 */
class GeigerClicker(context: Context) {

    private val appContext = context.applicationContext
    private val audioManager =
        appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    @Volatile
    private var rate = 0f

    @Volatile
    private var running = false

    @Volatile
    private var focusLost = false

    private var thread: Thread? = null
    private var focusRequest: AudioFocusRequest? = null

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    /** Clicks per second; thread-safe, applied within one render chunk. */
    fun setRate(clicksPerSecond: Float) {
        rate = clicksPerSecond
    }

    fun start() {
        if (running) return
        running = true
        focusLost = false

        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener { change ->
                focusLost = change == AudioManager.AUDIOFOCUS_LOSS ||
                    change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
            }
            .build()
        focusRequest = request
        if (audioManager.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            focusLost = true // keep rendering silence; focus may be granted later
        }

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
        val click = ClickWaveform.pcm16(SAMPLE_RATE)
        val minBuffer = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val track = AudioTrack(
            attributes,
            AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build(),
            maxOf(minBuffer, CHUNK_FRAMES * 4),
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE,
        )
        try {
            track.play()
            val chunk = ShortArray(CHUNK_FRAMES)
            var clickPos = -1
            var framesToNext = 0L
            var lastRate = -1f
            var lastDndCheckAt = 0L
            var dndBlocked = false

            while (running) {
                val now = System.currentTimeMillis()
                if (now - lastDndCheckAt >= DND_CHECK_MILLIS) {
                    lastDndCheckAt = now
                    dndBlocked = !Feedback.dndAllowsFeedback(appContext)
                }
                val r = if (focusLost || dndBlocked) 0f else rate
                if (r != lastRate) {
                    // Exponential intervals are memoryless: resampling on a
                    // rate change is statistically exact, not an approximation.
                    lastRate = r
                    framesToNext = intervalFrames(r)
                }
                for (i in chunk.indices) {
                    if (clickPos < 0 && framesToNext <= 0) {
                        if (r > 0f) {
                            clickPos = 0
                            framesToNext = intervalFrames(r)
                        } else {
                            framesToNext = CHUNK_FRAMES.toLong() // re-check next chunk
                        }
                    }
                    framesToNext--
                    if (clickPos in click.indices) {
                        chunk[i] = click[clickPos]
                        clickPos++
                        if (clickPos >= click.size) clickPos = -1
                    } else {
                        chunk[i] = 0
                    }
                }
                // Blocking write paces the loop at real time.
                track.write(chunk, 0, chunk.size)
            }
        } finally {
            runCatching {
                track.stop()
            }
            track.release()
        }
    }

    private fun intervalFrames(rate: Float): Long {
        val seconds = ClickRate.nextIntervalSeconds(rate, Random.nextFloat())
        if (seconds == Float.POSITIVE_INFINITY) return Long.MAX_VALUE
        return (seconds * SAMPLE_RATE).toLong().coerceAtLeast(1L)
    }

    companion object {
        private const val SAMPLE_RATE = 44_100
        /** ~46 ms per chunk: rate changes and DND/focus apply promptly. */
        private const val CHUNK_FRAMES = 2_048
        private const val DND_CHECK_MILLIS = 1_000L
    }
}
