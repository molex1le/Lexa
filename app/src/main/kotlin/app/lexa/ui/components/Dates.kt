package app.lexa.ui.components

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DayMonth = DateTimeFormatter.ofPattern("d MMMM", Locale.forLanguageTag("ru"))

/** «12 октября» in the device zone: dates in lists and on the word card. */
fun formatDay(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    DayMonth.format(Instant.ofEpochMilli(millis).atZone(zone))
