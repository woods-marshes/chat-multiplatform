package com.github.woodsmarshes.chat.core.ui.resources

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import cafe.adriel.lyricist.LanguageTag
import cafe.adriel.lyricist.Lyricist
import cafe.adriel.lyricist.ProvideStrings
import cafe.adriel.lyricist.rememberStrings
import com.github.woodsmarshes.chat.lyricist.Locales
import com.github.woodsmarshes.chat.lyricist.Strings
import com.github.woodsmarshes.chat.lyricist.ZhStrings

public val LyricistStrings: Map<LanguageTag, Strings> = mapOf(
    Locales.Zh to ZhStrings
)

public val LocalStrings: ProvidableCompositionLocal<Strings> =
    staticCompositionLocalOf { ZhStrings }

@Composable
public fun rememberLyricistStrings(
    defaultLanguageTag: LanguageTag = Locales.Zh,
    currentLanguageTag: LanguageTag = androidx.compose.ui.text.intl.Locale.current.toLanguageTag(),
): Lyricist<Strings> =
    rememberStrings(LyricistStrings, defaultLanguageTag, currentLanguageTag)

@Composable
public fun ProvideLyricistStrings(
    lyricist: Lyricist<Strings> = rememberLyricistStrings(),
    content: @Composable () -> Unit
) {
    ProvideStrings(lyricist, LocalStrings, content)
}

public fun getLocaleStrings(): Strings = ZhStrings
