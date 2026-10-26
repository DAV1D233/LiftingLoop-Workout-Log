package com.fitlog.app.domain

data class ExerciseTutorial(val title: String, val uploader: String, val bvid: String) {
    val url get() = "https://www.bilibili.com/video/$bvid/"
}

/** Public Bilibili pages checked on 2026-10-10. Never substitute a different exercise variant. */
object ExerciseTutorials {
    private fun kai(title: String, bvid: String) = ExerciseTutorial(title, "凯圣王", bvid)
    val videos = mapOf(
        "杠铃卧推" to kai("卧推详解", "BV1kx4y1U714"),
        "上斜哑铃卧推" to kai("上斜哑铃卧推详解", "BV1aa4y1o7XA"),
        "蝴蝶机夹胸" to kai("蝴蝶机夹胸详解", "BV1fTyVYDE1j"),
        "双杠臂屈伸" to ExerciseTutorial("如何正确地做双杠臂屈伸？", "蔡梓强", "BV1U4411t7VM"),
        "引体向上" to ExerciseTutorial("引体向上-动作讲解", "速练健身", "BV1YG411S7Pp"),
        "杠铃划船" to kai("杠铃划船练背思路", "BV1uaPNewEGe"),
        "高位下拉" to ExerciseTutorial("练背王牌动作 高位下拉动作详解，细节纠错", "Ariel_宇", "BV1bM411A76w"),
        "坐姿划船" to kai("坐姿划船详解", "BV1UG411k7ZM"),
        "反手高位下拉" to kai("反手高位下拉详解", "BV1KCetz4EbY"),
        "山羊挺身" to ExerciseTutorial("如何正确的做“山羊挺身”", "FE健身干货百科书", "BV1uE411w7ZL"),
        "杠铃深蹲" to ExerciseTutorial("保姆级深蹲教程：教你如打造一个强有力的杠铃深蹲", "UP健身", "BV1zm4y1j7Cn"),
        "腿举" to ExerciseTutorial("腿部训练教学：如何完成一个标准的腿举", "UP健身", "BV1Rd4y1c7vX"),
        "罗马尼亚硬拉" to kai("罗马尼亚硬拉详解", "BV1Zt421g7p5"),
        "俯卧腿弯举" to ExerciseTutorial("俯卧腿弯举器使用技巧，动作细节讲解", "健身教练刘远", "BV1Lr4y1w73U"),
        "站姿提踵" to ExerciseTutorial("站姿提踵训练", "Fortnightmmm", "BV1ExpEzZEkB"),
        "单腿硬拉" to ExerciseTutorial("健身新手必看，单腿硬拉深度教学。", "ACE宋健鹏", "BV1pMdGYHE7G"),
        "保加利亚单腿蹲" to ExerciseTutorial("保加利亚单腿蹲•详细教学", "小金鱼鱼儿", "BV13iHCe9EjU"),
        "高脚杯深蹲" to ExerciseTutorial("不要一上来就上杠铃深蹲！高脚杯深蹲了解一下", "运动科学", "BV1aa4y1R7TL"),
        "哑铃坐姿推举" to kai("坐姿哑铃推肩详解", "BV1uw411N7MC"),
        "哑铃侧平举" to kai("哑铃侧平举详解", "BV1UN41157u9"),
        "绳索面拉" to kai("绳索面拉详解", "BV1wZ421e7K2"),
        "坐姿反向飞鸟" to ExerciseTutorial("肩膀塑形｜坐姿反向飞鸟的训练技巧和细节", "Chloe彤哥", "BV1w3411z7V5"),
        "哑铃弯举" to kai("哑铃弯举详解（兄弟们新年快乐，看视频臂围增加3厘米）", "BV1Fp4y1d73D"),
        "锤式弯举" to ExerciseTutorial("如何正确地做锤式弯举？", "蔡梓强", "BV1iJ411U72E"),
        "绳索下压" to ExerciseTutorial("肱三头肌绳索下压｜手臂训练必学动作", "ALEX健身频道", "BV1fjMyzZEt5"),
        "仰卧臂屈伸" to kai("仰卧臂屈伸详解", "BV1pD421H7zw"),
        "凳上反屈伸" to ExerciseTutorial("紧致手臂，居家锻炼消除拜拜肉，凳上臂屈伸的正确做法", "Ariel_宇", "BV1jMSYYnE4Q"),
        "卷腹" to ExerciseTutorial("卷腹不是仰卧起坐！学会正确姿势，练腹不受伤！", "Steve不是美队", "BV1GS4y1f7Z9"),
        "悬垂举腿" to kai("悬垂举腿详解", "BV1nJ4m1e7bj"),
        "死虫式" to ExerciseTutorial("“死虫式”到底该咋练？", "运动康复小七", "BV1Ez4y1g7c4")
    )
    fun forExercise(name: String): ExerciseTutorial? = videos[name.trim()]
}
