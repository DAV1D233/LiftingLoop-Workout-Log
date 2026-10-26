package com.fitlog.app.ui.workout

import com.fitlog.app.ui.components.*

import com.fitlog.app.ui.components.AppCard
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fitlog.app.domain.*
import com.fitlog.app.ui.theme.*

/** Shared read-only action detail for saved records and the just-completed session. */
@Composable
internal fun WorkoutActionCard(index: Int, sets: List<LoggedSet>) {
    if (sets.isEmpty()) return
    val first = sets.first()
    AppCard(color = CardFill, shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ExerciseIcon(ExerciseSpec(name = first.name, isCardio = first.isCardio), Modifier.size(56.dp))
                Column(Modifier.weight(1f)) {
                    Text("${index + 1}".padStart(2, '0'), color = Muted, style = MaterialTheme.typography.labelMedium)
                    Text(first.name, color = Ink, style = MaterialTheme.typography.titleLarge)
                    Text(if (first.isCardio) { if (sets.size > 1) "${sets.size} 轮" else "有氧记录" } else "${sets.size} 组", color = Muted,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            if (first.isCardio) {
                val seconds = sets.sumOf { it.durationSeconds }
                val meters = sets.sumOf { it.distanceMeters }
                Text("有效运动 ${cardioTime(seconds)}" + if (meters > 0) " · ${number(meters)} 米" else "", color = Green)
                cardioPerformance(first.name, seconds, meters, first.poolLengthMeters > 0 || first.name.contains("泳"))?.let { Text(it, color = Green) }
            }
            sets.forEachIndexed { ordinal, set ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!set.isCardio || sets.size > 1) Text("${ordinal + 1}".padStart(2, '0'), color = Muted,
                        modifier = Modifier.width(24.dp), style = MaterialTheme.typography.labelLarge)
                    if (set.isCardio) {
                        val details = listOfNotNull(cardioTime(set.durationSeconds),
                            set.distanceMeters.takeIf { it > 0 }?.let { "${number(it)} 米" },
                            set.reps.takeIf { it > 0 }?.let { "$it 次" },
                            set.stroke.takeIf { it.isNotBlank() },
                            set.poolLengthMeters.takeIf { it > 0 }?.let { "池长 ${number(it)} 米" })
                        Surface(Modifier.weight(1f), color = FieldFill, shape = RoundedCornerShape(14.dp)) {
                            Text(details.joinToString(" · "), color = Ink, style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(12.dp))
                        }
                    } else {
                        RecordMetric(number(set.weight), "kg", Modifier.weight(1f))
                        Text("×", color = Muted)
                        RecordMetric("${set.reps}", "次", Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordMetric(value: String, unit: String, modifier: Modifier) {
    Surface(modifier, color = FieldFill, shape = RoundedCornerShape(14.dp)) {
        Text("$value $unit", color = Ink, style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp))
    }
}
