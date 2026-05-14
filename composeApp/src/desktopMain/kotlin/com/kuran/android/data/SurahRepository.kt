package com.kuran.android.data

import com.kuran.android.models.SurahInfo
import java.io.File

object SurahRepository {
    private var surahs: List<SurahInfo> = emptyList()
    private var alafasyDir: File? = null

    private val SURAH_FOLDER_MAP = mapOf(
        "Fatiha" to 1, "Bakara" to 2, "Al-i_İmran" to 3, "Nisa" to 4, "Maide" to 5,
        "Enam" to 6, "Araf" to 7, "Enfal" to 8, "Tevbe" to 9, "Yunus" to 10,
        "Hud" to 11, "Yusuf" to 12, "Rad" to 13, "Ibrahim" to 14, "Hicr" to 15,
        "Nahl" to 16, "Isra" to 17, "Kehf" to 18, "Meryem" to 19, "Taha" to 20,
        "Enbiya" to 21, "Hac" to 22, "Müminun" to 23, "Nur" to 24, "Furkan" to 25,
        "Şuara" to 26, "Neml" to 27, "Kasas" to 28, "Ankebut" to 29, "Rum" to 30,
        "Lokman" to 31, "Secde" to 32, "Ahzab" to 33, "Sebe" to 34, "Fatir" to 35,
        "Yasin" to 36, "Saffat" to 37, "Sad" to 38, "Zumar" to 39, "Mumin" to 40,
        "Fussilet" to 41, "Şura" to 42, "Zukhruf" to 43, "Duhan" to 44, "Casiye" to 45,
        "Ahkaf" to 46, "Muhammed" to 47, "Feth" to 48, "Hucurat" to 49, "Kaf" to 50,
        "Zariyat" to 51, "Tur" to 52, "Necm" to 53, "Kamer" to 54, "Rahman" to 55,
        "Vakia" to 56, "Mücadele" to 57, "Haşr" to 58, "Mumtehine" to 59, "Saff" to 60,
        "Cuma" to 61, "Munafik" to 62, "Teğabun" to 63, "Talak" to 64, "Tahrım" to 65,
        "Kalem" to 66, "Hakka" to 67, "Mearic" to 68, "Nuh" to 69, "Cin" to 70,
        "Muzzemmil" to 71, "Müddessir" to 72, "Kiyame" to 73, "Insan" to 74, "Mürselat" to 75,
        "Nebe" to 76, "Naziat" to 77, "Abasa" to 78, "Tekvin" to 79, "Infitar" to 80,
        "Mutaffifin" to 81, "Inşikak" to 82, "Buruc" to 83, "Tarık" to 84, "Ala" to 85,
        "Ghasik" to 86, "Fecr" to 87, "Balad" to 88, "Şems" to 89, "Leyl" to 90,
        "Duha" to 91, "Şerh" to 92, "Tin" to 93, "Alak" to 94, "Asr" to 95,
        "Fil" to 96, "Kureyş" to 97, "Kevser" to 98, "Kâfirûn" to 99, "Nasr" to 100,
        "Leheb" to 101, "Tekasur" to 102, "Asr" to 103, "Humeza" to 104, "Fil" to 105,
        "Kureyş" to 106, "Maun" to 107, "Kevser" to 108, "Kâfirûn" to 109, "Nasr" to 110,
        "Leheb" to 111, "İhlas" to 112, "Felak" to 113, "Nas" to 114
    )

    private val SURAH_NAMES = listOf(
        "Fatiha", "Bakara", "Al-i Imran", "Nisa", "Maide", "Enam", "Araf", "Enfal", "Tevbe",
        "Yunus", "Hud", "Yusuf", "Rad", "Ibrahim", "Hijr", "Nahl", "Isra", "Kehf", "Meryem",
        "Taha", "Enbiya", "Hajj", "Muminun", "Nur", "Furkan", "Şuara", "Neml", "Kasas", "Ankebut",
        "Rum", "Lokman", "Secde", "Ahzab", "Sebe", "Fatir", "Yasin", "Saffat", "Sad", "Zumar",
        "Mumin", "Fussilet", "Şura", "Zukhruf", "Duhan", "Casiye", "Ahkaf", "Muhammed", "Feth",
        "Hucurat", "Kaf", "Zariyat", "Tur", "Necm", "Kamer", "Rahman", "Vakia", "Mücadele",
        "Haşr", "Mumtehine", "Saff", "Cuma", "Munafik", "Teğabun", "Talak", "Tahrım", "Kalem",
        "Hakka", "Mearic", "Nuh", "Cin", "Muzzemmil", "Müddessir", "Kiyame", "Insan", "Mürselat",
        "Nebe", "Naziat", "Abasa", "Tekvin", "Infitar", "Mutaffifin", "Inşikak", "Buruc", "Tarık",
        "Ala", "Ghasik", "Fecr", "Balad", "Şems", "Leyl", "Duha", "Şerh", "Tin", "Alak"
    )

    fun initialize() {
        surahs = emptyList()
        alafasyDir = findAlafasyDir()
        if (alafasyDir == null || !alafasyDir!!.exists()) {
            return  // graceful fallback: boş liste
        }
        loadSurahs()
    }

    fun getSurahs(): List<SurahInfo> {
        if (surahs.isEmpty()) initialize()
        return surahs
    }

    fun getAyahFile(surah: SurahInfo, ayahNumber: Int): File? {
        if (alafasyDir == null) return null
        val folder = File(alafasyDir, surah.folderName)
        if (!folder.exists()) return null
        val fileName = "%03d%03d.mp3".format(surah.number, ayahNumber)
        val file = File(folder, fileName)
        return if (file.exists()) file else null
    }

    private fun findAlafasyDir(): File? {
        var current = File(System.getProperty("user.dir") ?: ".")
        for (i in 0..5) {
            val alafasy = File(current, "sureler/Alafasy")
            if (alafasy.exists() && alafasy.isDirectory) return alafasy
            current = current.parentFile ?: break
        }
        return null
    }

    private fun loadSurahs() {
        val dir = alafasyDir ?: return
        val folders = dir.listFiles { f -> f.isDirectory } ?: return

        surahs = folders
            .mapNotNull { folder ->
                val folderName = folder.name
                val surahNumber = SURAH_FOLDER_MAP[folderName] ?: return@mapNotNull null
                val displayName = if (surahNumber in 1..SURAH_NAMES.size)
                    "$surahNumber. ${SURAH_NAMES[surahNumber - 1]}"
                else
                    surahNumber.toString()
                val ayahCount = countAyahs(folder, surahNumber)
                SurahInfo(surahNumber, displayName, folderName, ayahCount)
            }
            .sortedBy { it.number }
    }

    private fun countAyahs(folder: File, surahNumber: Int): Int {
        val files = folder.listFiles { f ->
            f.isFile && f.name.endsWith(".mp3") &&
            f.name.startsWith("%03d".format(surahNumber))
        } ?: return 0
        return files.size
    }
}
