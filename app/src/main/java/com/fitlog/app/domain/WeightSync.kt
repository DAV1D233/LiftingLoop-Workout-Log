package com.fitlog.app.domain

object WeightSync {
    fun shouldImport(kg: Double, measuredAt: Long, currentUpdatedAt: Long, now: Long): Boolean =
        BodyWeight.valid(kg) && measuredAt > 0 && measuredAt <= now + 300000 && measuredAt > currentUpdatedAt
}
