package com.fitlog.app.ui.diet

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.fitlog.app.domain.*
import com.fitlog.app.ui.AppViewModel
import com.fitlog.app.ui.components.*
import com.fitlog.app.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

@Composable
fun FoodImage(path: String?, name: String, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(FieldFill), contentAlignment = Alignment.Center) {
        Text(name.take(1), color = Orange, style = MaterialTheme.typography.titleLarge)
        if (!path.isNullOrBlank()) AsyncImage(if (path.startsWith("/")) File(path) else path, name,
            Modifier.fillMaxSize().background(Paper), contentScale = ContentScale.Fit)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodDatePicker(initial: LocalDate, dismiss: () -> Unit, selected: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
    AppDatePickerDialog(dismiss, confirmButton = { AppTextButton(onClick = {
        state.selectedDateMillis?.let { selected(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
    }, enabled = state.selectedDateMillis != null) { Text("确定", color = Orange) } },
        dismissButton = { AppTextButton(onClick = dismiss) { Text("取消", color = Orange) } }) {
        DatePicker(state, colors = DatePickerDefaults.colors(todayDateBorderColor = androidx.compose.ui.graphics.Color.Transparent, selectedDayContainerColor = Orange,
            todayContentColor = Orange, selectedYearContainerColor = Orange))
    }
}

@Composable
private fun FoodNumber(label: String, value: String, change: (String) -> Unit, optional: Boolean = false) {
    val invalid = value.isNotBlank() && NutritionInput.amount(value) == null
    OutlinedTextField(value, change, Modifier.fillMaxWidth(), label = { Text(label) },
        singleLine = true, shape = RoundedCornerShape(14.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        isError = invalid, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Orange, focusedLabelColor = Orange, cursorColor = Orange),
        supportingText = if (invalid) ({ Text("请输入有效的非负数字") }) else if (optional) ({ Text("选填，留空表示未知") }) else null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodPortionDialog(initial: FoodPortion, dismiss: () -> Unit, confirm: (FoodPortion) -> Unit) {
    val food = initial.food
    var quantity by rememberSaveable(food.id) { mutableStateOf(foodNumber(initial.quantity)) }
    var useGrams by rememberSaveable(food.id) { mutableStateOf(false) }
    var calories by rememberSaveable(food.id) { mutableStateOf(food.calories?.let(::foodNumber) ?: "") }
    var protein by rememberSaveable(food.id) { mutableStateOf(food.protein?.let(::foodNumber) ?: "") }
    var carbs by rememberSaveable(food.id) { mutableStateOf(food.carbs?.let(::foodNumber) ?: "") }
    var fat by rememberSaveable(food.id) { mutableStateOf(food.fat?.let(::foodNumber) ?: "") }
    var error by remember { mutableStateOf(false) }
    val initialAmount = if (useGrams) initial.quantity * food.grams!! else initial.quantity
    val amount = if (quantity == foodNumber(initialAmount)) initial.quantity else
        NutritionInput.amount(quantity)?.let { if (useGrams) it / food.grams!! else it }
    fun parsed(text: String, original: Double?) = if (text == (original?.let(::foodNumber) ?: "")) original else NutritionInput.amount(text)
    val valid = amount != null && amount > 0 && amount <= 1000 && NutritionInput.canSave(food.name, calories, protein, carbs, fat)
    AppModalBottomSheet(dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Paper) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.9f).imePadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(food.name, style = MaterialTheme.typography.headlineMedium)
            Text("每份：${food.portion}", color = Muted)
            if (food.grams != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(false to "按份", true to "按克").forEach { (grams, label) ->
                    AppFilterChip(selected = useGrams == grams, onClick = {
                        quantity = amount?.let { foodNumber(if (grams) it * food.grams else it) } ?: ""
                        useGrams = grams
                    }, label = { Text(label) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = OrangeTint, selectedLabelColor = Orange))
                }
            }
            FoodNumber(if (useGrams) "本次食用 / 克" else "本次食用 / 份", quantity, { quantity = it; error = false })
            Text("以下营养值均按每份填写", color = Muted, style = MaterialTheme.typography.bodySmall)
            FoodNumber("每份热量 / kcal", calories, { calories = it; error = false })
            FoodNumber("每份蛋白质 / g", protein, { protein = it }, true)
            FoodNumber("每份碳水 / g", carbs, { carbs = it }, true)
            FoodNumber("每份脂肪 / g", fat, { fat = it }, true)
            Text(food.source, color = Muted, style = MaterialTheme.typography.bodySmall)
            if (error) Text("请填写热量和有效份量（大于0，最多1000份）", color = MaterialTheme.colorScheme.error)
            PrimaryAction("确定", color = Orange) {
                if (!valid) error = true else {
                    val changed = listOf(parsed(calories, food.calories), parsed(protein, food.protein), parsed(carbs, food.carbs), parsed(fat, food.fat)) != listOf(food.calories, food.protein, food.carbs, food.fat)
                    confirm(FoodPortion(food.copy(calories = parsed(calories, food.calories), protein = parsed(protein, food.protein),
                        carbs = parsed(carbs, food.carbs), fat = parsed(fat, food.fat),
                        source = if (changed) "用户校正 · ${food.source}" else food.source, estimated = changed || food.estimated), amount!!))
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CustomFoodSheet(vm: AppViewModel, original: FoodSpec?, dismiss: () -> Unit, saved: (FoodSpec) -> Unit) {
    val busy by vm.busy.collectAsState()
    val custom by vm.customFoods.collectAsState()
    var category by rememberSaveable { mutableStateOf(original?.category ?: "自定义") }
    var creatingTag by rememberSaveable { mutableStateOf(false) }
    var tagDraft by rememberSaveable { mutableStateOf("") }
    var tagError by rememberSaveable { mutableStateOf(false) }
    val tags = FoodTags.options(vm.foodLibrary + custom, category)
    var id by rememberSaveable { mutableStateOf(original?.id ?: "custom-${UUID.randomUUID()}") }
    var name by rememberSaveable { mutableStateOf(original?.name ?: "") }
    var portion by rememberSaveable { mutableStateOf(original?.portion ?: "1份") }
    var grams by rememberSaveable { mutableStateOf(original?.grams?.let(::foodNumber) ?: "") }
    var calories by rememberSaveable { mutableStateOf(original?.calories?.let(::foodNumber) ?: "") }
    var protein by rememberSaveable { mutableStateOf(original?.protein?.let(::foodNumber) ?: "") }
    var carbs by rememberSaveable { mutableStateOf(original?.carbs?.let(::foodNumber) ?: "") }
    var fat by rememberSaveable { mutableStateOf(original?.fat?.let(::foodNumber) ?: "") }
    var image by rememberSaveable { mutableStateOf(original?.image ?: "") }
    var importing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) scope.launch {
        importing = true
        try { image = vm.importFoodImage(uri) } catch (e: CancellationException) { throw e }
        catch (_: Exception) { vm.message("图片读取失败，请选择其他图片") } finally { importing = false }
    } }
    val valid = NutritionInput.canSave(name, calories, protein, carbs, fat) && portion.isNotBlank() &&
        (grams.isBlank() || NutritionInput.amount(grams)?.let { it > 0 && it <= 10000 } == true)
    fun preserved(text: String, value: Double?) = if (text == (value?.let(::foodNumber) ?: "")) value else NutritionInput.amount(text)
    AppModalBottomSheet({ if (!busy && !importing) dismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Paper) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.9f).imePadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (original == null) "添加自己的食物" else "编辑食物", style = MaterialTheme.typography.headlineMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FoodImage(image, name.ifBlank { "食" }, Modifier.size(76.dp))
                AppTextButton(onClick = { picker.launch("image/*") }, enabled = !busy && !importing) { Text(if (importing) "正在读取…" else "选择图片", color = Orange) }
            }
            OutlinedTextField(name, { name = it.take(120) }, Modifier.fillMaxWidth(), label = { Text("食物名称") }, singleLine = true, shape = RoundedCornerShape(16.dp))
            Text("标签", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tags.forEach { tag ->
                    AppFilterChip(selected = category == tag, onClick = { category = tag }, enabled = !busy,
                        label = { Text(tag) }, colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = OrangeTint, selectedLabelColor = Orange))
                }
                AppTextButton(onClick = { creatingTag = !creatingTag; tagError = false }, enabled = !busy) {
                    Text(if (creatingTag) "取消新建" else "＋ 新建标签", color = Orange)
                }
            }
            if (creatingTag) {
                OutlinedTextField(tagDraft, { tagDraft = it.take(20); tagError = false }, Modifier.fillMaxWidth(),
                    label = { Text("新标签名称") }, singleLine = true, enabled = !busy,
                    isError = tagError, supportingText = { Text(if (tagError) "请输入标签名称，不能使用“全部”" else "最多20字；同名标签会直接选中") },
                    shape = RoundedCornerShape(16.dp))
                AppTextButton(onClick = {
                    if (FoodTags.valid(tagDraft)) {
                        category = FoodTags.normalize(tagDraft); creatingTag = false; tagDraft = ""; tagError = false
                    } else tagError = true
                }, enabled = !busy) { Text("使用这个标签", color = Orange) }
            }
            OutlinedTextField(portion, { portion = it.take(60) }, Modifier.fillMaxWidth(), label = { Text("每份说明，例如 1碗、100克、1个") }, singleLine = true, shape = RoundedCornerShape(16.dp))
            FoodNumber("每份克数（选填）", grams, { grams = it }, true)
            FoodNumber("每份热量 / kcal", calories, { calories = it })
            FoodNumber("每份蛋白质 / g", protein, { protein = it }, true)
            FoodNumber("每份碳水 / g", carbs, { carbs = it }, true)
            FoodNumber("每份脂肪 / g", fat, { fat = it }, true)
            if (error) Text("请补全名称、份量和热量，克数如填写需大于0", color = MaterialTheme.colorScheme.error)
            PrimaryAction(if (busy) "保存中…" else "保存食物", !busy && !importing, Orange) {
                val selectedTag = if (creatingTag) tagDraft else category
                if (!FoodTags.valid(selectedTag)) tagError = true
                else if (!valid) error = true else {
                    val food = FoodSpec(id, name.trim(), "自定义", FoodTags.normalize(selectedTag), portion.trim(), preserved(grams, original?.grams),
                        preserved(calories, original?.calories), preserved(protein, original?.protein), preserved(carbs, original?.carbs), preserved(fat, original?.fat), image,
                        "用户填写", estimated = true)
                    if (!food.valid()) error = true else vm.saveCustomFood(food) { saved(food) }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
