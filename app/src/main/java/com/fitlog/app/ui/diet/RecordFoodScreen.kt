package com.fitlog.app.ui.diet

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.navigation.NavHostController
import com.fitlog.app.data.FoodJson
import com.fitlog.app.domain.*
import com.fitlog.app.ui.*
import com.fitlog.app.ui.components.*
import com.fitlog.app.ui.theme.*
import java.time.LocalDate

private val MealSaver = Saver<List<FoodPortion>, String>(save = { FoodJson.cart(it) }, restore = { FoodJson.cart(it) })

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecordFoodScreen(nav: NavHostController, vm: AppViewModel) {
    val custom by vm.customFoods.collectAsState()
    val busy by vm.busy.collectAsState()
    var chosen by rememberSaveable(stateSaver = MealSaver) { mutableStateOf(emptyList<FoodPortion>()) }
    var search by rememberSaveable { mutableStateOf("") }
    var brand by rememberSaveable { mutableStateOf("家常") }
    var category by rememberSaveable { mutableStateOf("全部") }
    var overview by rememberSaveable { mutableStateOf(false) }
    var dateRaw by rememberSaveable { mutableStateOf(nav.previousBackStackEntry?.savedStateHandle?.remove<Long>("foodDay")?.let(LocalDate::ofEpochDay)?.toString() ?: LocalDate.now().toString()) }
    var datePicker by remember { mutableStateOf(false) }
    var showCustom by remember { mutableStateOf(false) }
    var editCustom by remember { mutableStateOf<FoodSpec?>(null) }
    var manage by remember { mutableStateOf<FoodSpec?>(null) }
    var editPortion by remember { mutableStateOf<FoodPortion?>(null) }
    val libraryScroll = rememberLazyListState()
    LaunchedEffect(brand, category, search) { libraryScroll.scrollToItem(0) }
    val library = vm.foodLibrary + custom
    val categories = listOf("全部") + library.filter { it.brand == brand }.map { it.category }.distinct()
    val visible = library.filter { it.brand == brand && (category == "全部" || it.category == category) && it.name.contains(search.trim(), true) }
    fun back() { if (!busy) { if (overview) overview = false else nav.popBackStack() } }
    fun adjust(food: FoodSpec, delta: Double) {
        if (busy) return
        if (delta > 0 && chosen.size >= 200 && chosen.none { it.food.id == food.id }) {
            vm.message("一餐最多选择 200 种食物"); return
        }
        chosen = FoodPortions.adjust(chosen, food, delta)
    }
    BackHandler(overview || busy) { back() }
    PageScaffold(if (overview) "饮食概览" else "记录饮食", ::back, actions = {
        AppTextButton(onClick = { if (overview) overview = false else { editCustom = null; showCustom = true } }, enabled = !busy) {
            Text(if (overview) "继续添加" else "＋ 自定义", color = Orange)
        }
    }, bottom = {
        if (overview) PrimaryAction(if (busy) "保存中…" else "记录这一餐", chosen.isNotEmpty() && !busy, Orange) {
            if (!FoodNutrition.canSave(chosen)) { editPortion = chosen.firstOrNull { !it.ready() }; vm.message("请先补全待填食物的每份热量") }
            else vm.saveMeal(chosen, LocalDate.parse(dateRaw)) {
                nav.previousBackStackEntry?.savedStateHandle?.set("savedFoodDay", LocalDate.parse(dateRaw).toEpochDay())
                chosen = emptyList(); nav.popBackStack()
            }
        } else Surface(color = OrangeAccent, contentColor = Ink, shape = RoundedCornerShape(28.dp)) {
            Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 10.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.ShoppingCart, null)
                Column(Modifier.weight(1f)) {
                    Text("已选 ${chosen.size} 种食物", style = MaterialTheme.typography.titleMedium)
                    Text(FoodNutrition.total(chosen, "calories")?.let { (if (chosen.any { x -> x.food.estimated }) "约 " else "") + "${foodNumber(it)} kcal" } ?: if (chosen.isEmpty()) "挑选这一餐的食物" else "含待填热量", style = MaterialTheme.typography.bodySmall)
                }
                AppButton(onClick = { overview = true }, enabled = chosen.isNotEmpty() && !busy,
                    colors = ButtonDefaults.buttonColors(containerColor = OrangeTint, contentColor = Orange)) { Text("查看概览") }
            }
        }
    }) { padding ->
        if (overview) LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                MealNutritionRing(chosen, Modifier.fillMaxWidth())
                AppTextButton(onClick = { datePicker = true }, enabled = !busy, contentPadding = PaddingValues(0.dp)) {
                    Text(dateLabel(LocalDate.parse(dateRaw)) + "　⌄", color = Ink, style = MaterialTheme.typography.titleLarge)
                }
            }
            items(chosen, key = { it.food.id }) { item ->
                AppCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FoodImage(item.food.image, item.food.name, Modifier.size(64.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.food.name, style = MaterialTheme.typography.titleLarge)
                            Text(item.food.portion, color = Muted, style = MaterialTheme.typography.bodySmall)
                            Text(item.food.calories?.let { "${foodNumber(it * item.quantity)} kcal" } ?: "待填热量", color = Orange)
                        }
                        AppIconButton(onClick = { chosen = chosen.filterNot { it.food.id == item.food.id } }, enabled = !busy) { Icon(Icons.Default.Delete, "移除${item.food.name}", tint = Muted) }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        AppIconButton(onClick = { adjust(item.food, -1.0) }, enabled = !busy) { Text("−", color = Orange, style = MaterialTheme.typography.titleLarge) }
                        Text("${foodNumber(item.quantity)} 份", color = Orange, modifier = Modifier.padding(horizontal = 12.dp))
                        AppIconButton(onClick = { adjust(item.food, 1.0) }, enabled = !busy && item.quantity < 1000) { Icon(Icons.Default.Add, "增加一份") }
                        Spacer(Modifier.weight(1f))
                        AppTextButton(onClick = { editPortion = item }, enabled = !busy) { Text("编辑 ↗", color = Orange, style = MaterialTheme.typography.bodySmall) }
                    }
                    Text(item.food.source, color = Muted, style = MaterialTheme.typography.labelSmall)
                } }
            }
        } else Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), singleLine = true,
                placeholder = { Text("搜索食物") }, leadingIcon = { Icon(Icons.Default.Search, null) }, shape = RoundedCornerShape(18.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Orange, focusedLabelColor = Orange, cursorColor = Orange))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("家常", "肯德基", "麦当劳", "自定义").forEach { label -> AppFilterChip(selected = brand == label, onClick = { brand = label; category = "全部" }, label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = OrangeTint, selectedLabelColor = Orange)) }
            }
            Text(if (brand == "肯德基") "参考菜单 · 可能含停售餐品，待填热量可在概览补全" else if (brand == "自定义") "加减调整份数 · 长按可编辑或删除" else "每次加减一份", color = Muted,
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            Column(Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.forEach { label -> AppFilterChip(selected = category == label, onClick = { category = label },
                        label = { Text(label) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = OrangeTint, selectedLabelColor = Orange)) }
                }
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = libraryScroll,
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (visible.isEmpty()) item { Text("没有找到食物，可用右上角添加自己的食物。", color = Muted, modifier = Modifier.padding(16.dp)) }
                    items(visible, key = { it.id }) { food ->
                        val picked = chosen.firstOrNull { it.food.id == food.id }
                        val quantity = picked?.quantity ?: 0.0
                        val selected = picked != null
                        AppCard(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).motionCombinedClickable(enabled = !busy,
                            onClick = { if (picked != null) editPortion = picked else adjust(food, 1.0) },
                            onLongClickLabel = if (food.brand == "自定义") "编辑或删除食物" else "查看食物资料",
                            onLongClick = { if (food.brand == "自定义") manage = food else editPortion = chosen.firstOrNull { it.food.id == food.id } ?: FoodPortion(food) }),
                            color = if (selected) OrangeTint else CardFill, shape = RoundedCornerShape(20.dp)) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                FoodImage(food.image, food.name, Modifier.size(80.dp))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text(food.name, style = MaterialTheme.typography.titleMedium)
                                    Text(food.portion, style = MaterialTheme.typography.bodySmall, color = Muted)
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(food.calories?.let { (if (food.estimated) "约 " else "") + "${foodNumber(it)} kcal" } ?: "待填热量",
                                            color = Orange, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            AppIconButton(onClick = { adjust(food, -1.0) }, enabled = !busy && selected,
                                                modifier = Modifier.size(36.dp)) {
                                                Text("−", color = if (selected) Orange else Muted.copy(alpha = .4f),
                                                    style = MaterialTheme.typography.titleMedium,
                                                    modifier = Modifier.semantics { contentDescription = "${food.name}减少一份，减至零移除" })
                                            }
                                            Text("${foodNumber(quantity)} 份", color = if (selected) Orange else Muted,
                                                style = MaterialTheme.typography.bodySmall,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.widthIn(min = 28.dp))
                                            AppIconButton(onClick = { adjust(food, 1.0) }, enabled = !busy && quantity < 1000,
                                                modifier = Modifier.size(36.dp)) {
                                                Icon(Icons.Default.Add, "${food.name}增加一份", tint = Orange, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (datePicker) FoodDatePicker(LocalDate.parse(dateRaw), { datePicker = false }) { dateRaw = it.toString(); datePicker = false }
    if (showCustom) CustomFoodSheet(vm, editCustom, { showCustom = false }) { food ->
        chosen = chosen.map { if (it.food.id == food.id) it.copy(food = food) else it }; brand = "自定义"; category = "全部"; showCustom = false
    }
    manage?.let { food -> AppAlertDialog(onDismissRequest = { manage = null }, title = { Text(food.name) },
        confirmButton = { AppTextButton(onClick = { manage = null; editCustom = food; showCustom = true }) { Text("编辑") } },
        dismissButton = { AppTextButton(onClick = { manage = null; vm.deleteCustomFood(food) }) { Text("删除", color = Muted) } }) }
    editPortion?.let { item -> FoodPortionDialog(item, { editPortion = null }) { updated ->
        if (chosen.any { it.food.id == updated.food.id }) chosen = chosen.map { if (it.food.id == updated.food.id) updated else it }
        else if (chosen.size < 200) chosen = chosen + updated
        editPortion = null
    } }
}
