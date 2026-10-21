package com.ima.imaloader.youtube

object YouTubeUrlParser {

    private val ID_REGEX = Regex(
        """(?:youtube(?:-nocookie)?\.com/(?:[^/]+/.+/|(?:v|e(?:mbed)?)/|.*[?&]v=)|youtu\.be/)([A-Za-z0-9_-]{11})"""
    )
    private val SHORTS_REGEX = Regex("""youtube\.com/shorts/([A-Za-z0-9_-]{11})""")
    private val RAW_ID_REGEX = Regex("""^[A-Za-z0-9_-]{11}$""")

    /** Devuelve el ID del video o null si el enlace no es válido. */
    fun extractVideoId(url: String?): String? {
        val trimmed = url?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        if (RAW_ID_REGEX.matches(trimmed)) return trimmed
        SHORTS_REGEX.find(trimmed)?.let { return it.groupValues[1] }
        ID_REGEX.find(trimmed)?.let { return it.groupValues[1] }
        return null
    }

    /** URL canónica que el usuario puede pegar en el descargador. */
    fun canonicalUrl(videoId: String) = "https://www.youtube.com/watch?v=$videoId"

    /** Miniatura oficial (no requiere API key). */
    fun thumbnailUrl(videoId: String) =
        "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
}
