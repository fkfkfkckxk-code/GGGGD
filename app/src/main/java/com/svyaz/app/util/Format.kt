package com.svyaz.app.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

/*
 * Мелкие помощники: телефон, ник и красивый вывод времени.
 */

/**
 * Приводит номер к международному виду: «8 (916) 123-45-67» → «+79161234567».
 * Возвращает null, если введённое не похоже на номер телефона.
 */
fun normalizePhone(input: String): String? {
    val trimmed = input.trim()
    val digits = trimmed.filter { it in '0'..'9' }
    val result = when {
        trimmed.startsWith("+") -> "+$digits"
        digits.length == 11 && digits.startsWith("8") -> "+7" + digits.drop(1)
        digits.length == 10 -> "+7$digits"
        else -> "+$digits"
    }
    // В международном номере от 8 до 15 цифр
    return if (result.length in 9..16) result else null
}

/** Ник: латинские буквы, цифры и «_», от 3 до 20 символов. */
private val NICKNAME_REGEX = Regex("^[a-z0-9_]{3,20}$")

fun isValidNickname(nickname: String): Boolean = NICKNAME_REGEX.matches(nickname)

/** Убирает из введённого ника всё лишнее: «@Ivan Петров» → «ivan». */
fun cleanNickname(input: String): String =
    input.lowercase()
        .filter { it in 'a'..'z' || it in '0'..'9' || it == '_' }
        .take(20)

private val RU: Locale = Locale.forLanguageTag("ru")
private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm", RU)
private val DAY_MONTH_SHORT = DateTimeFormatter.ofPattern("d MMM", RU)
private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMMM", RU)
private val DAY_MONTH_YEAR = DateTimeFormatter.ofPattern("d MMMM yyyy", RU)
private val SHORT_DATE = DateTimeFormatter.ofPattern("dd.MM.yy", RU)

private fun Date.toLocal() = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault())

/** Время сообщения: «14:05». */
fun formatTime(date: Date): String = date.toLocal().format(TIME_FORMAT)

/** Время в списке чатов: сегодня — «14:05», в этом году — «3 мар.», иначе «03.03.24». */
fun formatChatListTime(date: Date): String {
    val local = date.toLocal()
    val today = LocalDate.now()
    return when {
        local.toLocalDate() == today -> local.format(TIME_FORMAT)
        local.year == today.year -> local.format(DAY_MONTH_SHORT)
        else -> local.format(SHORT_DATE)
    }
}

/** Заголовок дня в переписке: «Сегодня», «Вчера», «3 марта». */
fun formatDayHeader(date: Date): String {
    val local = date.toLocal()
    val day = local.toLocalDate()
    val today = LocalDate.now()
    return when {
        day == today -> "Сегодня"
        day == today.minusDays(1) -> "Вчера"
        day.year == today.year -> local.format(DAY_MONTH)
        else -> local.format(DAY_MONTH_YEAR)
    }
}

/** Проверяет, что две даты приходятся на один календарный день. */
fun isSameDay(a: Date, b: Date): Boolean = a.toLocal().toLocalDate() == b.toLocal().toLocalDate()

/** Длительность: 75000 мс → «1:15». */
fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
