package com.hikari.app.ui.russian

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.hikari.app.domain.russian.RuProsody
import java.io.File
import java.nio.ByteOrder

/** Dekodiert Opus-/AAC-Clips zu 16-kHz-Mono-PCM (Float) für die Prosodie-Analyse. */
internal object RussianAudioDecoder {

    fun decodeAsset(context: Context, name: String): FloatArray? = runCatching {
        context.assets.openFd("russian/audio/$name.ogg").use { afd ->
            val ex = MediaExtractor()
            try {
                ex.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                decode(ex)
            } finally {
                ex.release()
            }
        }
    }.getOrNull()

    fun decodeFile(file: File): FloatArray? = runCatching {
        val ex = MediaExtractor()
        try {
            ex.setDataSource(file.absolutePath)
            decode(ex)
        } finally {
            ex.release()
        }
    }.getOrNull()

    private fun decode(ex: MediaExtractor): FloatArray? {
        val track = (0 until ex.trackCount).firstOrNull {
            ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: return null
        ex.selectTrack(track)
        val format = ex.getTrackFormat(track)
        val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(format, null, null, 0)
        codec.start()
        var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val out = ArrayList<Float>(sampleRate * 4)
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        try {
            while (!outputDone) {
                if (!inputDone) {
                    val i = codec.dequeueInputBuffer(10_000)
                    if (i >= 0) {
                        val buf = codec.getInputBuffer(i)!!
                        val n = ex.readSampleData(buf, 0)
                        if (n < 0) {
                            codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(i, 0, n, ex.sampleTime, 0)
                            ex.advance()
                        }
                    }
                }
                val o = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    o >= 0 -> {
                        val buf = codec.getOutputBuffer(o)!!
                        buf.order(ByteOrder.LITTLE_ENDIAN)
                        val shorts = buf.asShortBuffer()
                        val frames = shorts.remaining() / channels
                        for (f in 0 until frames) {
                            var sum = 0f
                            for (c in 0 until channels) sum += shorts.get() / 32768f
                            out.add(sum / channels)
                        }
                        codec.releaseOutputBuffer(o, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                    o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val nf = codec.outputFormat
                        sampleRate = nf.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = nf.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        // PCM-Encoding ist bei Audio-Decodern 16 Bit, sofern nichts anderes gemeldet wird.
                        if (nf.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                            nf.getInteger(MediaFormat.KEY_PCM_ENCODING) != AudioFormat.ENCODING_PCM_16BIT
                        ) return null
                    }
                }
            }
        } finally {
            codec.stop()
            codec.release()
        }
        return resample(out.toFloatArray(), sampleRate, RuProsody.SAMPLE_RATE)
    }

    private fun resample(src: FloatArray, from: Int, to: Int): FloatArray {
        if (from == to || src.isEmpty()) return src
        val n = (src.size.toLong() * to / from).toInt()
        return FloatArray(n) { i ->
            val pos = i.toDouble() * from / to
            val i0 = pos.toInt().coerceAtMost(src.size - 1)
            val i1 = (i0 + 1).coerceAtMost(src.size - 1)
            val frac = (pos - i0).toFloat()
            src[i0] * (1 - frac) + src[i1] * frac
        }
    }
}
