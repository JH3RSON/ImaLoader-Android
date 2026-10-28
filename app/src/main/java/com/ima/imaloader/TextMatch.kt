package com.ima.imaloader

import java.text.Normalizer

object TextMatch {
    private val NOISE = Regex(
        """(?i)\s*[\(\[\{【「『][^)\]\}】」』]*\b(official|oficial|video|vídeo|audio|lyrics?|letra|visuali[sz]er|mv|m/v|hd|4k|remaster(ed)?|subtitulad[oa]|clip|full|ver\.?)\b[^)\]\}】」』]*[\)\]\}】」』]"""
    )
    private val TAIL = Regex("""\s*[|｜/／].*$""")
    private val FEAT = Regex(
        """(?i)[\(\[【「]\s*(feat|ft|featuring|con)\b[^)\]】」]*[\)\]】」]|\s+(feat|ft|featuring)\.?\s+[^\-–—(\[【「]*$"""
    )
    private val VARIANTS = listOf(
        "live", "en vivo", "remix", "cover", "acoustic", "acústico",
        "instrumental", "karaoke", "slowed", "sped up", "nightcore", "8d"
    ).map { it to Regex("""(?i)\b${Regex.escape(it)}\b""") }

    fun cleanTitle(t: String): String =
        t.replace(NOISE, "").replace(TAIL, "").trim()

    fun normalize(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(FEAT, " ")
            .replace(Regex("[^\\p{L}\\p{N} ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun tokens(s: String): Set<String> =
        normalize(s).split(" ").filter { it.isNotEmpty() }.toSet()

    fun similarity(a: String, b: String): Double {
        val x = tokens(a)
        val y = tokens(b)
        if (x.isEmpty() || y.isEmpty()) return 0.0
        val i = x.intersect(y).size.toDouble()
        val union = (x.size + y.size - i).coerceAtLeast(1.0)
        val minSize = minOf(x.size, y.size).toDouble().coerceAtLeast(1.0)
        return (0.5 * i / union) + (0.5 * i / minSize)
    }

    /** live / remix / cover…: si difieren entre original y candidata, es otra versión */
    fun variantTags(raw: String): Set<String> =
        VARIANTS.filter { (_, re) -> re.containsMatchIn(raw) }.map { it.first }.toSet()

    fun splitArtistTitle(t: String): Pair<String?, String> {
        val p = t.split(" - ", " – ", " — ", limit = 2)
        return if (p.size == 2 && p[0].isNotBlank() && p[1].isNotBlank()) {
            p[0].trim() to p[1].trim()
        } else {
            null to t
        }
    }
}