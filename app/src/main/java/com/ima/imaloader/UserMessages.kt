package com.ima.imaloader

fun friendlyError(url: String, e: DownloadException, hasSession: Boolean = false): String {
    val p = Platform.detect(url)
    return when (e.kind) {
        ErrorKind.RESTRICTED ->
            if (hasSession)
                "Tu cuenta de ${p.label} tampoco puede ver este contenido. Puede ser privado o tener restricción de edad o región. Prueba con otra cuenta."
            else
                "Este contenido tiene audiencia restringida (edad, región o cuenta privada). Inicia sesión en ${p.label} con una cuenta que pueda verlo."
        ErrorKind.LOGIN -> "${p.label} pide iniciar sesión para este contenido."
        ErrorKind.EXTRACTOR ->
            if (p.loginUrl != null) "No pude leer este video. Si es privado o de una cuenta cerrada, inicia sesión en ${p.label}; si no, actualiza el motor."
            else "No pude leer este enlace. Actualiza el motor e inténtalo de nuevo."
        ErrorKind.NETWORK -> "Sin conexión estable. Reintentaré cuando vuelva la red."
        ErrorKind.UNSUPPORTED -> "Este enlace no es compatible."
        ErrorKind.STORAGE -> "No hay espacio suficiente."
        else -> e.message ?: "Error desconocido"
    }
}

fun shouldOfferLogin(url: String, e: DownloadException) =
    Platform.detect(url).loginUrl != null &&
        e.kind in setOf(ErrorKind.LOGIN, ErrorKind.EXTRACTOR, ErrorKind.RESTRICTED)
