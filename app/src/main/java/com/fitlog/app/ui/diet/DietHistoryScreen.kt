package com.fitlog.app.ui.diet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.fitlog.app.data.FoodRecord
import com.fitlog.app.domain.foodNumber
import com.fitlog.app.ui.*
import com.fitlog.app.ui.components.*
import com.fitlog.app.ui.theme.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private fun completeNutrition(items: List<FoodRecord>, field: (FoodRecord) -> Double?): Double? =
    items.takeIf { it.isNotEmpty() && it.all { food -> field(food) != null } }?.sumOf { field(it)!! }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DietHistoryScreen(nav: NavHostController, vm: AppViewModel) {
    val data by vm.data.collectAsState()
    val entryDay = remember { nav.previousBackStackEntry?.savedStateHandle?.remove<Long>("dietDay")?.let(LocalDate::ofEpochDay) ?: LocalDate.now() }
    var selectedRaw by rememberSaveable { mutableStateOf(entryDay.toString()) }
    var monthRaw by rememberSaveable { mutableStateOf(YearMonth.from(entryDay).toString()) }
    var detail by remember { mutableStateOf<FoodRecord?>(null) }
    var deleting by remember { mutableStateOf<FoodRecord?>(null) }
    var picker by remember { mutableStateOf(false) }
    val savedDay by remember { nav.currentBackStackEntry?.savedStateHandle?.getStateFlow("savedFoodDay", Long.MIN_VALUE)
        ?: kotlinx.coroutines.flow.MutableStateFlow(Long.MIN_VALUE) }.collectAsState()
    LaunchedEffect(savedDay) {
        if (savedDay != Long.MIN_VALUE) {
            val day = LocalDate.ofEpochDay(savedDay)
            selectedRaw = day.toString(); monthRaw = YearMonth.from(day).toString()
        }
    }
    val selected = LocalDate.parse(selectedRaw)
    val month = YearMonth.parse(monthRaw)
    val byDay = remember(data.foods) { data.foods.groupBy { it.dateEpochDay } }
    val meals = byDay[selected.toEpochDay()].orEmpty()
    val monthFoods = remember(data.foods, month) { data.foods.filter { YearMonth.from(LocalDate.ofEpochDay(it.dateEpochDay)) == month } }
    val gridStart = month.atDay(1).minusDays((month.atDay(1).dayOfWeek.value - 1).toLong())
    val rows = (month.atDay(1).dayOfWeek.value - 1 + month.lengthOfMonth() + 6) / 7
    fun record() { nav.currentBackStackEntry?.savedStateHandle?.set("foodDay", selected.toEpochDay()); nav.navigate(Routes.FOOD) }
    fun changeMonth(delta: Long) {
        val next = month.plusMonths(delta); monthRaw = next.toString()
        selectedRaw = next.atDay(minOf(selected.dayOfMonth, next.lengthOfMonth())).toString()
    }
    PageScaffold("饮食日历", { nav.popBackStack() }, actions = {
        AppTextButton(onClick = ::record) { Text("＋ 记录", color = Orange) }
    }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppIconButton(onClick = { changeMonth(-1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "上个月") }
                    AppTextButton(onClick = { picker = true }, modifier = Modifier.weight(1f)) {
                        Text("${month.year} / ${month.monthValue.toString().padStart(2, '0')}", style = MaterialTheme.typography.headlineMedium, color = Ink)
                    }
                    AppIconButton(onClick = { changeMonth(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "下个月") }
                }
                Row(Modifier.fillMaxWidth()) { listOf("一", "二", "三", "四", "五", "六", "日").forEach {
                    Text(it, Modifier.weight(1f).padding(vertical = 8.dp), textAlign = TextAlign.Center, color = Muted)
                } }
                repeat(rows) { row -> Row(Modifier.fillMaxWidth()) { repeat(7) { column ->
                    val date = gridStart.plusDays((row * 7 + column).toLong())
                    val foods = byDay[date.toEpochDay()].orEmpty()
                    val inMonth = YearMonth.from(date) == month
                    val isSelected = date == selected
                    Column(Modifier.weight(1f).heightIn(min = 72.dp).motionClickable {
                        selectedRaw = date.toString(); monthRaw = YearMonth.from(date).toString()
                    }.semantics { contentDescription = "${dateLabel(date)}，${foods.size} 条饮食记录" }.padding(vertical = 7.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(34.dp).background(when { isSelected -> OrangeAccent; foods.isNotEmpty() && inMonth -> OrangeTint; else -> Color.Transparent }, CircleShape), contentAlignment = Alignment.Center) {
                            Text("${date.dayOfMonth}", color = when { isSelected -> Ink; !inMonth -> Muted.copy(alpha = .28f); else -> Ink }, fontSize = 15.sp)
                        }
                        if (foods.isNotEmpty() && inMonth) Text(foodNumber(foods.sumOf { it.calories }), color = Orange,
                            style = MaterialTheme.typography.labelSmall, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
                    }
                } } }
                Text("本月记录 ${monthFoods.map { it.dateEpochDay }.distinct().size} 天", color = Orange, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp))
                HorizontalDivider(color = Line)
                Text(dateLabel(selected), color = Ink, fontSize = 27.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 22.dp, bottom = 10.dp))
                Text("${foodNumber(meals.sumOf { it.calories })} kcal", color = Orange, style = MaterialTheme.typography.headlineMedium)
                Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf("蛋白质" to completeNutrition(meals) { it.proteinGrams }, "碳水" to completeNutrition(meals) { it.carbsGrams }, "脂肪" to completeNutrition(meals) { it.fatGrams }).forEach { (name, value) ->
                        Column { Text(name, color = Muted, style = MaterialTheme.typography.bodySmall); Text(value?.let { "${foodNumber(it)} g" } ?: "未完整记录", color = Ink, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
            if (meals.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(vertical = 30.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("这天还没有饮食记录", style = MaterialTheme.typography.titleLarge)
                    Text("选择食物，记下这一餐。", color = Muted)
                    AppTextButton(onClick = ::record, contentPadding = PaddingValues(0.dp)) { Text("记录饮食 →", color = Orange) }
                }
            }
            items(meals, key = { it.id }) { food ->
                AppCard(Modifier.fillMaxWidth().clickable { detail = food }, shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FoodImage(food.photoPath, food.name, Modifier.size(66.dp))
                        Column(Modifier.weight(1f)) {
                            Text(food.name, style = MaterialTheme.typography.titleMedium)
                            Text("${foodNumber(food.calories)} kcal", color = Orange, modifier = Modifier.padding(top = 6.dp))
                        }
                        Text(Instant.ofEpochMilli(food.createdAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm")), color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
    if (picker) FoodDatePicker(selected, { picker = false }) { selectedRaw = it.toString(); monthRaw = YearMonth.from(it).toString(); picker = false }
    detail?.let { food ->
        AppModalBottomSheet(onDismissRequest = { detail = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Paper) {
            Column(Modifier.fillMaxWidth().fillMaxHeight(.85f).verticalScroll(rememberScrollState()).padding(24.dp)) {
                Text(dateLabel(LocalDate.ofEpochDay(food.dateEpochDay)), color = Orange)
                FoodImage(food.photoPath, food.name, Modifier.fillMaxWidth().height(170.dp).padding(top = 12.dp))
                Text(food.name, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(vertical = 18.dp))
                Text("${foodNumber(food.calories)} kcal", color = Orange, style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(18.dp))
                listOf("蛋白质" to food.proteinGrams, "碳水" to food.carbsGrams, "脂肪" to food.fatGrams).forEach { (name, value) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(name, color = Muted); Text(value?.let { "${foodNumber(it)} g" } ?: "未记录")
                    }
                    HorizontalDivider(color = Line)
                }
                AppTextButton(onClick = { detail = null; deleting = food }, modifier = Modifier.padding(top = 18.dp)) { Text("删除这条记录", color = Muted) }
            }
        }
    }
    deleting?.let { food -> DeleteDialog(food.name, { deleting = null }) { vm.deleteFood(food); deleting = null } }
}
