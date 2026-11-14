package com.freetia.tv.data.parser

sealed class ParseResult<T> {
    data class Success<T>(val data: T, val metadata: Map<String, String> = emptyMap()) : ParseResult<T>()
    data class Partial<T>(val data: T, val errors: List<String>) : ParseResult<T>()
    data class Error<T>(val exception: Exception, val partialData: T? = null) : ParseResult<T>()
}

data class M3uChannel(
    val tvgId: String = "",
    val tvgName: String = "",
    val tvgLogo: String = "",
    val tvgCountry: String = "",
    val tvgLanguage: String = "",
    val tvgType: String = "",
    val groupTitle: String = "",
    val name: String,
    val streamUrl: String,
    val number: Int = 0,
    val catchupEnabled: Boolean = false,
    val catchupDays: Int = 7,
    val rawAttributes: Map<String, String> = emptyMap()
)

data class M3uPlaylist(
    val channels: List<M3uChannel>,
    val tvgUrl: String = "",
    val tvgShift: String = "0",
    val tvgProviders: String = "",
    val rawHeader: Map<String, String> = emptyMap()
)

data class XmltvChannel(
    val id: String,
    val displayName: String = "",
    val icon: String = ""
)

data class XmltvProgramme(
    val start: Long,
    val stop: Long,
    val channel: String,
    val title: String,
    val description: String = "",
    val category: String = "",
    val icon: String = "",
    val rating: Int = 0,
    val length: Int = 0,
    val previouslyShown: Boolean = false
)

data class XmltvData(
    val channels: List<XmltvChannel>,
    val programmes: List<XmltvProgramme>
)
