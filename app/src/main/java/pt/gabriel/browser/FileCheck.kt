package pt.gabriel.browser

/**
 * Confere se o conteúdo de um ficheiro corresponde à sua extensão, pelos primeiros bytes
 * ("assinatura" ou magic bytes). Um .jpg tem de começar por FF D8 FF, um .mp4 tem "ftyp" na posição 4, etc.
 *
 * Limites: só lê o início do ficheiro. Não prova que o ficheiro está completo nem que é inofensivo;
 * formatos desconhecidos ou de texto não geram aviso (para evitar falsos alarmes).
 */
object FileCheck {

    data class Verdict(val ok: Boolean, val ext: String, val label: String?, val dangerous: Boolean)

    private class Kind(val label: String, val exts: Set<String>, val exec: Boolean = false)

    private fun k(label: String, vararg exts: String, exec: Boolean = false) = Kind(label, exts.toSet(), exec)

    private val JPEG = k("JPEG", "jpg", "jpeg", "jpe", "jfif")
    private val PNG = k("PNG", "png", "apng")
    private val GIF = k("GIF", "gif")
    private val WEBP = k("WebP", "webp")
    private val BMP = k("BMP", "bmp", "dib")
    private val ICO = k("ICO", "ico", "cur")
    private val TIFF = k("TIFF", "tif", "tiff")
    private val ISO = k("MP4/MOV/HEIC", "mp4", "m4v", "m4a", "m4b", "mov", "3gp", "3g2", "heic", "heif", "avif", "f4v")
    private val EBML = k("WebM/MKV", "webm", "mkv", "mka")
    private val OGG = k("OGG", "ogg", "ogv", "oga", "opus", "spx")
    private val FLAC = k("FLAC", "flac")
    private val MP3 = k("MP3", "mp3", "mp2")
    private val AAC = k("AAC", "aac", "adts")
    private val WAV = k("WAV", "wav")
    private val AVI = k("AVI", "avi")
    private val PDF = k("PDF", "pdf")
    private val ZIP = k("ZIP/APK/Office", "zip", "apk", "apks", "xapk", "aab", "jar", "docx", "xlsx", "pptx",
        "odt", "ods", "odp", "epub", "xpi", "cbz", "kmz", "war", "ipa")
    private val GZ = k("GZIP", "gz", "tgz")
    private val RAR = k("RAR", "rar")
    private val SEVENZ = k("7-Zip", "7z")
    private val OLE = k("Office antigo (OLE)", "doc", "xls", "ppt", "msi", "msg")
    private val EXE = k("Executável Windows (EXE)", "exe", "dll", "scr", "com", exec = true)
    private val ELF = k("Executável Linux (ELF)", "so", "elf", "bin", "o", "out", "ko", exec = true)
    private val DEX = k("Código Android (DEX)", "dex", exec = true)
    private val SCRIPT = k("Script", "sh", "bash", "py", "pl", "rb", "run", "command", exec = true)
    private val HTML = k("HTML", "html", "htm", "xhtml", "shtml", "php")
    private val SVG = k("SVG", "svg", "svgz")

    private val ALL = listOf(
        JPEG, PNG, GIF, WEBP, BMP, ICO, TIFF, ISO, EBML, OGG, FLAC, MP3, AAC, WAV, AVI, PDF, ZIP, GZ, RAR,
        SEVENZ, OLE, EXE, ELF, DEX, SCRIPT, HTML, SVG
    )
    private val KNOWN_EXTS: Set<String> = ALL.flatMap { it.exts }.toSet()

    private fun u(b: ByteArray, i: Int): Int = if (i in b.indices) b[i].toInt() and 0xFF else -1
    private fun starts(b: ByteArray, vararg v: Int): Boolean = v.indices.all { u(b, it) == v[it] }
    private fun at(b: ByteArray, off: Int, s: String): Boolean = s.indices.all { u(b, off + it) == s[it].code }

    private fun detect(b: ByteArray): Kind? = when {
        starts(b, 0xFF, 0xD8, 0xFF) -> JPEG
        starts(b, 0x89, 0x50, 0x4E, 0x47) -> PNG
        at(b, 0, "GIF8") -> GIF
        at(b, 0, "RIFF") && at(b, 8, "WEBP") -> WEBP
        at(b, 0, "RIFF") && at(b, 8, "WAVE") -> WAV
        at(b, 0, "RIFF") && at(b, 8, "AVI ") -> AVI
        at(b, 0, "BM") && u(b, 6) == 0 && u(b, 7) == 0 && u(b, 8) == 0 && u(b, 9) == 0 -> BMP
        starts(b, 0, 0, 1, 0) -> ICO
        starts(b, 0x49, 0x49, 0x2A, 0) || starts(b, 0x4D, 0x4D, 0, 0x2A) -> TIFF
        at(b, 4, "ftyp") || at(b, 4, "moov") || at(b, 4, "mdat") || at(b, 4, "free") || at(b, 4, "wide") -> ISO
        starts(b, 0x1A, 0x45, 0xDF, 0xA3) -> EBML
        at(b, 0, "OggS") -> OGG
        at(b, 0, "fLaC") -> FLAC
        at(b, 0, "ID3") -> MP3
        u(b, 0) == 0xFF && (u(b, 1) and 0xE0) == 0xE0 -> if ((u(b, 1) and 0x06) == 0) AAC else MP3
        at(b, 0, "%PDF") -> PDF
        starts(b, 0x50, 0x4B, 3, 4) || starts(b, 0x50, 0x4B, 5, 6) -> ZIP
        starts(b, 0x1F, 0x8B) -> GZ
        at(b, 0, "Rar!") -> RAR
        starts(b, 0x37, 0x7A, 0xBC, 0xAF) -> SEVENZ
        starts(b, 0xD0, 0xCF, 0x11, 0xE0) -> OLE
        at(b, 0, "MZ") -> EXE
        starts(b, 0x7F, 0x45, 0x4C, 0x46) -> ELF
        at(b, 0, "dex\n") -> DEX
        at(b, 0, "#!") -> SCRIPT
        else -> detectText(b)
    }

    private fun detectText(b: ByteArray): Kind? {
        var i = 0
        if (starts(b, 0xEF, 0xBB, 0xBF)) i = 3
        while (i < b.size && (b[i].toInt() == 0x20 || b[i].toInt() in 9..13)) i++
        val sb = StringBuilder()
        while (i < b.size && sb.length < 24) {
            val c = b[i].toInt() and 0xFF
            if (c >= 0x80) break
            sb.append(c.toChar().lowercaseChar())
            i++
        }
        val s = sb.toString()
        return when {
            s.startsWith("<!doctype html") || s.startsWith("<html") || s.startsWith("<head") ||
                s.startsWith("<body") || s.startsWith("<script") || s.startsWith("<?php") -> HTML
            s.startsWith("<svg") -> SVG
            else -> null
        }
    }

    /** [head]: os primeiros bytes do ficheiro (até 64). [name]: nome final com extensão. */
    fun check(head: ByteArray, name: String): Verdict {
        val ext = name.substringAfterLast('.', "").lowercase()
        if (head.isEmpty()) {
            return if (ext in KNOWN_EXTS) Verdict(false, ext, "vazio (0 bytes)", false) else Verdict(true, ext, null, false)
        }
        val kind = detect(head) ?: return Verdict(true, ext, null, false)
        if (ext in kind.exts) return Verdict(true, ext, kind.label, false)
        return if (kind.exec || ext in KNOWN_EXTS) Verdict(false, ext, kind.label, kind.exec)
        else Verdict(true, ext, kind.label, false)
    }
}
