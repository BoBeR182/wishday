package io.github.wishday.ui

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.wishday.R
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import io.github.wishday.ui.theme.LocalIsDark
import io.github.wishday.ui.theme.LocalPalette
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.wishday.birthday.BirthdayStats
import io.github.wishday.birthday.Birthdays
import io.github.wishday.data.Person
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(vm: WishdayViewModel, onDone: () -> Unit, onOpen: (Long) -> Unit) {
    val people by vm.people.collectAsState()
    val today = remember { LocalDate.now() }
    val stats = remember(people) { BirthdayStats.of(people, today) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stats)) },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        if (stats.total == 0) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.add_birthdays_for_stats), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(stringResource(R.string.stat_people), stats.total.toString(), Modifier.weight(1f))
                StatTile(stringResource(R.string.stat_this_month), stats.thisMonth.toString(), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(stringResource(R.string.stat_next_30), stats.next30Days.toString(), Modifier.weight(1f))
                StatTile(
                    stringResource(R.string.stat_average_age),
                    stats.averageAge?.let { "%.0f".format(it) } ?: "–",
                    Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(stringResource(R.string.stat_important), stats.important.toString(), Modifier.weight(1f))
                StatTile(stringResource(R.string.stat_gift_reminders), stats.withGiftReminder.toString(), Modifier.weight(1f))
            }

            Section(stringResource(R.string.stats_by_month)) { MonthChart(stats, today) }

            if (stats.milestones.isNotEmpty()) {
                Section(stringResource(R.string.stats_milestones)) {
                    stats.milestones.forEach { (person, age) ->
                        val days = Birthdays.daysUntil(person, today)
                        PersonLine(
                            person, stringResource(R.string.turns_age, age),
                            when (days) {
                                0L -> stringResource(R.string.today_cake)
                                1L -> stringResource(R.string.when_tomorrow)
                                else -> pluralStringResource(R.plurals.when_in_days, days.toInt(), days.toInt())
                            },
                            onOpen,
                        )
                    }
                }
            }

            if (stats.youngest != null && stats.oldest != null && stats.youngest != stats.oldest) {
                Section(stringResource(R.string.stats_youngest_oldest)) {
                    PersonLine(
                        stats.youngest, stringResource(R.string.youngest),
                        stringResource(R.string.turns_age, Birthdays.ageTurning(stats.youngest, today)!!), onOpen,
                    )
                    PersonLine(
                        stats.oldest, stringResource(R.string.oldest),
                        stringResource(R.string.turns_age, Birthdays.ageTurning(stats.oldest, today)!!), onOpen,
                    )
                }
            }

            if (stats.twins.isNotEmpty()) {
                Section(stringResource(R.string.stats_twins)) {
                    stats.twins.forEach { group ->
                        Text(
                            "${shortDate(group.first())} · ${group.joinToString(", ") { it.name }}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                }
            }

            Section(stringResource(R.string.stats_star_signs)) {
                stats.zodiac.chunked(2).forEach { row ->
                    Row(Modifier.padding(vertical = 4.dp)) {
                        row.forEach { (sign, count) ->
                            Text(
                                "${sign.symbol}  ${stringResource(sign.label)}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                count.toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(40.dp),
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f).width(40.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(22.dp))
            .padding(16.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(22.dp))
            .padding(16.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

/**
 * One series, so no legend: the title names it. The current month is a second color *and*
 * labelled, so it never relies on color alone. Tapping a bar shows its exact count.
 */
@Composable
private fun MonthChart(stats: BirthdayStats, today: LocalDate) {
    val dark = LocalIsDark.current
    val palette = LocalPalette.current
    // Each theme's pair was validated against its card surface (lightness band, contrast, color-blind separation).
    val barColor = if (dark) palette.chartBarDark else palette.chartBarLight
    val nowColor = if (dark) palette.chartNowDark else palette.chartNowLight
    val locale = LocalConfiguration.current.locales[0]
    val current = today.monthValue
    var selected by rememberSaveable { mutableStateOf<Int?>(null) }
    val max = stats.byMonth.max().coerceAtLeast(1)

    val res = LocalContext.current.resources
    fun monthName(m: Int, style: TextStyle = TextStyle.FULL) =
        Month.of(m).getDisplayName(style, locale).replaceFirstChar { it.titlecase(locale) }
    fun countText(n: Int) = res.getQuantityString(R.plurals.birthdays_count, n, n)

    val caption = selected?.let { res.getString(R.string.chart_month_count, monthName(it), countText(stats.byMonth[it - 1])) }
        ?: when (stats.busiestMonths.size) {
            0 -> ""
            1 -> res.getString(
                R.string.chart_busiest,
                monthName(stats.busiestMonths[0]),
                countText(stats.byMonth[stats.busiestMonths[0] - 1]),
            )
            else -> {
                val each = stats.byMonth[stats.busiestMonths[0] - 1]
                val months = stats.busiestMonths.joinToString(res.getString(R.string.and_separator)) { monthName(it, TextStyle.SHORT) }
                res.getQuantityString(R.plurals.chart_busiest_tie, each, each, months)
            }
        }
    Text(caption, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(12.dp))

    Row(
        modifier = Modifier.fillMaxWidth().height(140.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp), // surface gap between adjacent bars
        verticalAlignment = Alignment.Bottom,
    ) {
        (1..12).forEach { m ->
            val count = stats.byMonth[m - 1]
            val isBusiest = m in stats.busiestMonths
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    // The whole column is the tap target, not just the bar.
                    .clickable { selected = if (selected == m) null else m }
                    .semantics { contentDescription = res.getString(R.string.chart_month_count, monthName(m), countText(count)) },
            ) {
                // Direct labels only where they matter: the busiest month(s) and the selected one.
                // Every column reserves the same label slot so bar heights stay comparable.
                Box(Modifier.height(18.dp), contentAlignment = Alignment.BottomCenter) {
                    if ((isBusiest || selected == m) && count > 0) {
                        Text(count.toString(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }
                if (count > 0) {
                    Box(
                        Modifier
                            .padding(horizontal = 3.dp)
                            .fillMaxWidth()
                            .fillMaxHeight(count.toFloat() / max)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(
                                (if (m == current) nowColor else barColor)
                                    .copy(alpha = if (selected == null || selected == m) 1f else 0.45f),
                            ),
                    )
                }
            }
        }
    }
    // Baseline the bars sit on.
    Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        (1..12).forEach { m ->
            Text(
                monthName(m, TextStyle.NARROW),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (m == current) FontWeight.ExtraBold else null,
                color = if (m == current) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(nowColor))
        Spacer(Modifier.width(6.dp))
        Text(
            stringResource(R.string.chart_legend, monthName(current)),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PersonLine(person: Person, label: String, detail: String, onOpen: (Long) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onOpen(person.id) }
            .padding(vertical = 6.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(person.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "${shortDate(person)} · $label",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(detail, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

