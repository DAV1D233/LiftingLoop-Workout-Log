package com.fitlog.app.ui.diet

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitlog.app.domain.FoodNutrition
import com.fitlog.app.domain.FoodPortion
import com.fitlog.app.domain.foodNumber
import com.fitlog.app.ui.theme.*

@Composable
fun MealNutritionRing(items: List<FoodPortion>, modifier: Modifier = Modifier) {
    val shares = remember(items) { FoodNutrition.energyShares(items) }
    val values = remember(items) { listOf("protein", "carbs", "fat").map { FoodNutrition.total(items, it) } }
    val names = listOf("蛋白质", "碳水", "脂肪")
    val colors = listOf(GreenAccent, OrangeAccent, Blue)
    val calories = FoodNutrition.total(items, "calories")
    val complete = values.all { it != null }
    val summary = names.mapIndexed { i, name -> "$name ${values[i]?.let { "${foodNumber(it)} 克" } ?: "未知"}" }.joinToString("，")
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp), contentAlignment = Alignment.Center) {
            BoxWithConstraints(Modifier.sizeIn(maxWidth = 220.dp, maxHeight = 220.dp).aspectRatio(1f)
                .semantics { contentDescription = "三大营养素供能占比，$summary" }, contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = size.minDimension * .095f
                    val inset = stroke / 2
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    drawCircle(FieldFill, radius = (size.minDimension - stroke) / 2, style = Stroke(stroke))
                    if (shares != null) {
                        var start = -90f
                        val count = shares.count { it > 0 }
                        shares.forEachIndexed { index, fraction ->
                            val sweep = (fraction * 360).toFloat()
                            val gap = if (count > 1) minOf(2.5f, sweep * .15f) else 0f
                            if (sweep > 0) drawArc(colors[index], start + gap / 2, sweep - gap, false,
                                Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Butt))
                            start += sweep
                        }
                    }
                }
                val innerWidth = maxWidth * .64f
                Column(Modifier.width(innerWidth), horizontalAlignment = Alignment.CenterHorizontally) {
                    val text = calories?.let { (if (items.any { food -> food.food.estimated }) "约 " else "") + foodNumber(it) } ?: "—"
                    val density = LocalDensity.current
                    val measurer = rememberTextMeasurer()
                    val style = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Medium)
                    val measured = measurer.measure(text, style, softWrap = false).size.width
                    val ratio = (with(density) { innerWidth.toPx() } / measured.coerceAtLeast(1)).coerceAtMost(1f)
                    Text(text, color = Ink, fontSize = 28.sp * ratio, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
                    Text("kcal", color = Muted, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 3.dp))
                    if (!complete) Text("营养待补全", color = Muted, style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 8.dp), textAlign = TextAlign.Center)
                }
            }
        }
        Text(if (shares != null) "三大营养素供能占比" else if (complete) "三项营养素均为 0" else "补全三项营养后显示占比",
            color = Muted, style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            names.forEachIndexed { index, name ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(Modifier.size(7.dp).background(colors[index], CircleShape))
                        Text(name, color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(values[index]?.let { "${foodNumber(it)} g" } ?: "未知", color = Ink,
                        style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 5.dp))
                    shares?.get(index)?.let { Text("${foodNumber(it * 100)}%", color = Muted,
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 3.dp)) }
                }
            }
        }
    }
}
