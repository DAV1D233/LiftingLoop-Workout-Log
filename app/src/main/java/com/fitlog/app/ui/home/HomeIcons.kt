package com.fitlog.app.ui.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.fitlog.app.R

internal enum class HomeSymbol { WORKOUT, FOOD, ADD, PLAY, FREE, CALENDAR, BOOK, PLAN, EDIT, HISTORY, WEIGHT }

@Composable
internal fun HomeIcon(symbol: HomeSymbol, tint: Color, modifier: Modifier = Modifier) {
    val drawable = when (symbol) {
        HomeSymbol.WORKOUT, HomeSymbol.FREE -> R.drawable.ic_home_workout
        HomeSymbol.FOOD -> R.drawable.ic_home_food
        HomeSymbol.WEIGHT -> R.drawable.ic_home_weight
        HomeSymbol.BOOK -> R.drawable.ic_home_book
        else -> null
    }
    if (drawable != null) Icon(painterResource(drawable), null, modifier, tint)
    else Icon(when (symbol) {
        HomeSymbol.ADD, HomeSymbol.PLAN -> Icons.Default.Add
        HomeSymbol.PLAY -> Icons.Default.PlayArrow
        HomeSymbol.CALENDAR -> Icons.Default.DateRange
        HomeSymbol.EDIT -> Icons.Default.Edit
        else -> Icons.AutoMirrored.Filled.List
    }, null, modifier, tint)
}
