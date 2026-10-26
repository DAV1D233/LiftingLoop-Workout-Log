package com.fitlog.app.ui.workout

import androidx.annotation.DrawableRes
import com.fitlog.app.R

/** Crops from the supplied reference; only equivalent movements share an illustration. */
@DrawableRes
internal fun exerciseIllustration(name: String): Int? = when (name) {
    "杠铃卧推" -> R.drawable.exercise_bench_press
    "上斜哑铃卧推" -> R.drawable.exercise_incline_dumbbell_press
    "双杠臂屈伸" -> R.drawable.exercise_parallel_bar_dip
    "仰卧臂屈伸" -> R.drawable.exercise_lying_triceps_extension
    "高位下拉" -> R.drawable.exercise_lat_pulldown
    "反手高位下拉" -> R.drawable.exercise_reverse_grip_pulldown
    "坐姿划船" -> R.drawable.exercise_seated_cable_row
    "正握开肘胸托T杠划船" -> R.drawable.exercise_chest_supported_t_bar_row
    "坐姿反向飞鸟" -> R.drawable.exercise_reverse_fly
    "凳上反屈伸" -> R.drawable.exercise_bench_dip
    "上斜仰卧杠铃臂屈伸" -> R.drawable.exercise_incline_barbell_extension
    "单腿硬拉" -> R.drawable.exercise_single_leg_deadlift
    "保加利亚单腿蹲" -> R.drawable.exercise_bulgarian_split_squat
    "高脚杯深蹲" -> R.drawable.exercise_goblet_squat
    "罗马尼亚硬拉" -> R.drawable.exercise_romanian_deadlift
    "山羊挺身" -> R.drawable.exercise_back_extension
    "蝴蝶机夹胸" -> R.drawable.exercise_pec_deck
    "引体向上" -> R.drawable.exercise_pull_up
    "杠铃划船" -> R.drawable.exercise_barbell_row
    "杠铃深蹲" -> R.drawable.exercise_barbell_squat
    "腿举" -> R.drawable.exercise_leg_press
    "俯卧腿弯举" -> R.drawable.exercise_lying_leg_curl
    "站姿提踵" -> R.drawable.exercise_standing_calf_raise
    "哑铃坐姿推举" -> R.drawable.exercise_seated_dumbbell_press
    "哑铃侧平举" -> R.drawable.exercise_lateral_raise
    "绳索面拉" -> R.drawable.exercise_face_pull
    "哑铃弯举" -> R.drawable.exercise_dumbbell_curl
    "锤式弯举" -> R.drawable.exercise_hammer_curl
    "绳索下压" -> R.drawable.exercise_triceps_pushdown
    "卷腹" -> R.drawable.exercise_crunch
    "悬垂举腿" -> R.drawable.exercise_hanging_leg_raise
    "死虫式" -> R.drawable.exercise_dead_bug
    "开合跳" -> R.drawable.exercise_jumping_jack
    "高抬腿" -> R.drawable.exercise_high_knees
    "登山跑" -> R.drawable.exercise_mountain_climber
    else -> null
}
