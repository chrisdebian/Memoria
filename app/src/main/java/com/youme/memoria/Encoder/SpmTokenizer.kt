package com.youme.memoria.Encoder

import android.content.Context
import java.text.Normalizer
import java.util.Locale

object SpmTokenizer {
    const val CONTEXT_LENGTH = 77
    private const val SPACE = "\u2581"
    private const val UNK = 1
    private const val TYPE_UNUSED = 5
    private const val TYPE_BYTE = 6

    private var pieceToId = HashMap<String, Int>()
    private var scores = FloatArray(0)
    private var types = IntArray(0)
    private val byteIds = IntArray(256) { -1 }

    var vocabSize = 0
        private set
    val bosId: Int get() = vocabSize
    val eosId: Int get() = vocabSize + 1

    @Synchronized
    fun init(context: Context) {
        if (vocabSize > 0) return
        init(context.assets.open("spm.model").use { it.readBytes() })
    }


    @Synchronized
    fun init(model: ByteArray) {
        val ids = HashMap<String, Int>()
        val sc = ArrayList<Float>()
        val ty = ArrayList<Int>()
        java.util.Arrays.fill(byteIds, -1)

        val r = Reader(model, 0, model.size)
        while (r.hasMore()) {
            val tag = r.varint().toInt()
            val field = tag ushr 3
            when (tag and 7) {
                0 -> r.varint()
                1 -> r.p += 8
                2 -> {
                    val len = r.varint().toInt()
                    if (field == 1) readPiece(model, r.p, r.p + len, ids, sc, ty)
                    r.p += len
                }
                5 -> r.p += 4
                else -> error("bad protobuf wire type")
            }
        }
        pieceToId = ids
        scores = sc.toFloatArray()
        types = ty.toIntArray()
        vocabSize = sc.size
    }

    private fun readPiece(
        b: ByteArray, start: Int, end: Int,
        ids: HashMap<String, Int>, sc: ArrayList<Float>, ty: ArrayList<Int>
    ) {
        val r = Reader(b, start, end)
        var piece = ""
        var score = 0f
        var type = 1
        while (r.hasMore()) {
            val tag = r.varint().toInt()
            val field = tag ushr 3
            when (tag and 7) {
                0 -> { val v = r.varint(); if (field == 3) type = v.toInt() }
                1 -> r.p += 8
                2 -> {
                    val len = r.varint().toInt()
                    if (field == 1) piece = String(b, r.p, len, Charsets.UTF_8)
                    r.p += len
                }
                5 -> { val v = r.fixed32(); if (field == 2) score = Float.fromBits(v) }
                else -> error("bad protobuf wire type")
            }
        }
        val index = sc.size
        if (!ids.containsKey(piece)) ids[piece] = index
        sc.add(score)
        ty.add(type)
        if (type == TYPE_BYTE && piece.length == 6 && piece.startsWith("<0x") && piece.endsWith(">")) {
            byteIds[piece.substring(3, 5).toInt(16)] = index
        }
    }

    private class Reader(val b: ByteArray, var p: Int, val end: Int) {
        fun hasMore() = p < end
        fun varint(): Long {
            var result = 0L
            var shift = 0
            while (true) {
                val x = b[p++].toInt() and 0xFF
                result = result or ((x and 0x7F).toLong() shl shift)
                if ((x and 0x80) == 0) return result
                shift += 7
            }
        }
        fun fixed32(): Int {
            val v = (b[p].toInt() and 0xFF) or
                    ((b[p + 1].toInt() and 0xFF) shl 8) or
                    ((b[p + 2].toInt() and 0xFF) shl 16) or
                    ((b[p + 3].toInt() and 0xFF) shl 24)
            p += 4
            return v
        }
    }

    private fun isDeleted(cp: Int) =
        cp in 0x01..0x08 || cp == 0x0B || cp in 0x0E..0x1F || cp == 0x7F || cp == 0x8F || cp == 0x9F


    private fun isSpaceLike(cp: Int) =
        cp == 0x200B || cp == 0x200C || cp == 0x200E || cp == 0x200F || cp == 0xFEFF || cp == 0xFFFD


    private val FOLD: Map<Int, Int> = mapOf(
        0x0345 to 0x03B9,
        0x03C2 to 0x03C3, 0x03F2 to 0x03C3,
        0x1C80 to 0x0432, 0x1C81 to 0x0434, 0x1C82 to 0x043E, 0x1C83 to 0x0441, 0x1C84 to 0x0442,
        0x1C85 to 0x0442, 0x1C86 to 0x044A, 0x1C87 to 0x0463, 0x1C88 to 0xA64B
    )

    private fun normalize(text: String): String {
        val nfkc = Normalizer.normalize(text, Normalizer.Form.NFKC).lowercase(Locale.ROOT)
        val sb = StringBuilder(nfkc.length)
        var i = 0
        while (i < nfkc.length) {
            val cp = nfkc.codePointAt(i)
            i += Character.charCount(cp)
            when {
                isDeleted(cp) -> {}
                isSpaceLike(cp) -> sb.append(' ')
                else -> sb.appendCodePoint(FOLD[cp] ?: cp)
            }
        }
        return sb.toString()
    }


    fun encodeIds(text: String): IntArray {
        check(vocabSize > 0) { "SpmTokenizer.init() was not called" }
        val norm = normalize(text)


        val words = ArrayList<String>()
        val cur = StringBuilder()
        for (ch in norm) {
            if (ch.isWhitespace()) {
                if (cur.isNotEmpty()) { words.add(cur.toString()); cur.setLength(0) }
            } else cur.append(ch)
        }
        if (cur.isNotEmpty()) words.add(cur.toString())
        if (words.isEmpty()) return IntArray(0)


        val full = SPACE + words.joinToString(SPACE)
        val out = ArrayList<Int>()
        var syms = ArrayList<String>()
        var i = 0
        while (i < full.length) {
            val cp = full.codePointAt(i)
            val s = String(Character.toChars(cp))
            if (s == SPACE && syms.isNotEmpty()) {
                bpeWord(syms, out)
                syms = ArrayList()
            }
            syms.add(s)
            i += Character.charCount(cp)
        }
        if (syms.isNotEmpty()) bpeWord(syms, out)
        return out.toIntArray()
    }

    private fun bpeWord(syms: ArrayList<String>, out: ArrayList<Int>) {
        while (true) {
            var best = -1
            var bestScore = 0f
            for (i in 0 until syms.size - 1) {
                val id = pieceToId[syms[i] + syms[i + 1]] ?: continue
                if (types[id] == TYPE_UNUSED) continue
                val s = scores[id]
                if (best < 0 || s > bestScore) { best = i; bestScore = s }
            }
            if (best < 0) break
            syms[best] = syms[best] + syms[best + 1]
            syms.removeAt(best + 1)
        }
        for (sy in syms) {
            val id = pieceToId[sy]
            if (id != null && types[id] != TYPE_UNUSED) {
                out.add(id)
            } else {
                for (byte in sy.toByteArray(Charsets.UTF_8)) {
                    val bid = byteIds[byte.toInt() and 0xFF]
                    out.add(if (bid >= 0) bid else UNK)
                }
            }
        }
    }
    fun encode(text: String): IntArray {
        val ids = encodeIds(text)
        val out = IntArray(CONTEXT_LENGTH)
        val n = minOf(ids.size, CONTEXT_LENGTH - 2)
        out[0] = bosId
        for (k in 0 until n) out[1 + k] = ids[k]
        out[1 + n] = eosId
        return out
    }
}