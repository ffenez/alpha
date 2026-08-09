package app.radiacode.device

import app.radiacode.protocol.Command
import app.radiacode.protocol.Vs

/**
 * Per-command response timeouts. SET_EXCHANGE is slow right after connect
 * (Open-RadiaCode-Android field experience: up to 25 s) and full spectrum
 * reads stream ~kilobytes over notifications (mkgeiger: 30 s budget).
 */
class Timeouts(
    val defaultMillis: Long = 12_000,
    val setExchangeMillis: Long = 25_000,
    val spectrumReadMillis: Long = 30_000,
) {

    fun forRequest(command: Int, args: ByteArray): Long = when {
        command == Command.SET_EXCHANGE -> setExchangeMillis
        command == Command.RD_VIRT_STRING && vsId(args) in SPECTRUM_VS_IDS -> spectrumReadMillis
        else -> defaultMillis
    }

    /** VS id is the leading u32 LE of RD_VIRT_STRING args. */
    private fun vsId(args: ByteArray): Int? {
        if (args.size < 4) return null
        return (args[0].toInt() and 0xFF) or
            ((args[1].toInt() and 0xFF) shl 8) or
            ((args[2].toInt() and 0xFF) shl 16) or
            ((args[3].toInt() and 0xFF) shl 24)
    }

    private companion object {
        val SPECTRUM_VS_IDS = setOf(Vs.SPECTRUM, Vs.SPEC_ACCUM, Vs.SPEC_DIFF)
    }
}
