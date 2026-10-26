package com.fitlog.app.domain

object TrainingStats {
    fun streak(days: Set<Long>, today: Long): Int {
        var cursor = if (today in days) today else today - 1
        var result = 0
        while (cursor in days) { result++; cursor-- }
        return result
    }
}
