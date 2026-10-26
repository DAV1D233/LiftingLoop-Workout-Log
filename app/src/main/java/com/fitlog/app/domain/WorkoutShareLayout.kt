package com.fitlog.app.domain

data class ShareAction(val exerciseIndex: Int, val name: String, val cardio: Boolean,
    val rows: List<List<CompletedSet>>)
data class MeasuredShareAction(val headerHeight: Float, val rowHeights: List<Float>)
data class ShareSlice(val action: Int, val fromRow: Int, val untilRow: Int)
data class ShareColumns(val capacity: Float, val columns: List<List<ShareSlice>>)

/** Group by identity, not name: two occurrences of the same exercise remain separate. */
object WorkoutShareLayout {
    /** Columns share one canvas. Increase its height instead of exporting extra pages. */
    fun columns(actions: List<MeasuredShareAction>, count: Int, minimumCapacity: Float): ShareColumns {
        require(count in 1..4 && minimumCapacity.isFinite() && minimumCapacity > 0)
        val required = actions.maxOfOrNull { it.headerHeight + (it.rowHeights.maxOrNull() ?: 0f) } ?: 0f
        var low = maxOf(minimumCapacity, required + 1f)
        var high = low
        while (pages(actions, high).size > count) { high *= 1.4f; require(high.isFinite()) }
        repeat(20) {
            val mid = (low + high) / 2
            if (pages(actions, mid).size <= count) high = mid else low = mid
        }
        return ShareColumns(high, pages(actions, high))
    }
    fun actions(sets: List<CompletedSet>): List<ShareAction> = sets.groupBy { it.exerciseIndex }.values.map { group ->
        val first = group.first()
        ShareAction(first.exerciseIndex, first.name, first.isCardio,
            group.sortedBy { it.ordinal }.chunked(if (first.isCardio) 1 else 3))
    }

    fun setText(set: CompletedSet): String = if (!set.isCardio) "${number(set.weight)}kg ×${set.reps}" else
        listOfNotNull(cardioTime(set.durationSeconds), set.distanceMeters.takeIf { it > 0 }?.let { "${number(it)} 米" },
            set.reps.takeIf { it > 0 && set.cardioMode == "计次" }?.let { "$it 次" },
            set.stroke.takeIf { it.isNotBlank() }, set.poolLengthMeters.takeIf { it > 0 }?.let { "池长 ${number(it)} 米" }).joinToString(" · ")

    /** Never drop sets or split a group heading from its first row. Continuations repeat the heading. */
    fun pages(actions: List<MeasuredShareAction>, capacity: Float): List<List<ShareSlice>> {
        require(capacity.isFinite() && capacity > 0)
        val pages = mutableListOf<List<ShareSlice>>()
        var page = mutableListOf<ShareSlice>()
        var used = 0f
        fun flush() { if (page.isNotEmpty()) { pages.add(page.toList()); page = mutableListOf(); used = 0f } }
        actions.forEachIndexed { index, action ->
            require(action.headerHeight.isFinite() && action.headerHeight >= 0)
            var from = 0
            while (from < action.rowHeights.size) {
                val first = action.rowHeights[from]
                require(first.isFinite() && first > 0 && action.headerHeight + first <= capacity)
                if (used + action.headerHeight + first > capacity) flush()
                used += action.headerHeight
                var until = from
                while (until < action.rowHeights.size) {
                    val height = action.rowHeights[until]
                    require(height.isFinite() && height > 0)
                    if (used + height > capacity) break
                    used += height
                    until++
                }
                page.add(ShareSlice(index, from, until))
                from = until
                if (from < action.rowHeights.size) flush()
            }
        }
        flush()
        return pages.ifEmpty { listOf(emptyList()) }
    }
}
