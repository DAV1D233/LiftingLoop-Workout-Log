# FitLog 完整界面配色 · 2.1.74 / 85

清单包含应用固定界面色、图表衍生色、海报、启动资源和 Material 3 全部浅色/深色角色。照片与插画像素、用户照片、外部小米网页和 Android 系统 UI 不属于固定主题色；玻璃折射后的像素会随背景变化。

## 本轮设计规则

- 首页三个入口及共用底座保留玻璃；其他按钮纯色、无描边、无模糊内缘，圆角保留、边缘清晰。
- 卡片 #F7F5EF → #F3F5F6；按钮/输入区域 #EFEEE8 → #E9EEF1；分隔线改为 #DDE3E7。
- 移除普通按钮和卡片阴影，防止透明底色把阴影透入内部。保留限定在控件形状内的短暂按压反馈与滑块切换。
- 取消的是控件外轮廓。输入框聚焦/错误指示、图标线条与图表轨道仍表达信息；首页光学边缘按用户确认保留。

## 基础与业务颜色

| 名称 | 色号 | 用途 |
|---|---|---|
| `Paper` | `#FFFFFF` | 页面白色 |
| `CardFill` | `#F3F5F6` | 中性浅灰卡片 |
| `FieldFill` | `#E9EEF1` | 按钮 / 输入区域 |
| `Line` | `#DDE3E7` | 内容分隔线 |
| `Ink` | `#1D2822` | 主要文字 |
| `Muted` | `#5F6B62` | 辅助文字 |
| `Emphasis` | `#111111` | 日历 / 历史日期标题 |
| `GreenAccent` | `#58C7AB` | 运动主色 / 主要操作 |
| `Green` | `#23745F` | 运动文字 / 图标 |
| `GreenTint` | `#E4F5F0` | 运动浅色选中底 |
| `OrangeAccent` | `#F2A03D` | 饮食主色 |
| `Orange` | `#925009` | 饮食文字 / 图标 |
| `OrangeTint` | `#FEEFDF` | 饮食浅色选中底 |
| `DockGreenTint` | `#AAE1D0` | 首页运动玻璃底色 |
| `DockOrangeTint` | `#FACC9D` | 首页饮食玻璃底色 |
| `DockGreenInk` | `#163F35` | 首页运动图标 |
| `DockOrangeInk` | `#5D3508` | 首页饮食图标 |
| `DockAdd` | `#000000` | 首页添加玻璃底色 |
| `Night` | `#101512` | 训练页背景 |
| `NightSurface` | `#202A23` | 训练按钮 / 环轨道 |
| `RestSurface` | `#303330` | 跳过休息按钮 |
| `RestText` | `#D2D5D2` | 跳过休息文字 |
| `Blue` | `#73B6D5` | 完成组 / 休息 / 脂肪 |
| `NightText` | `#F2F5ED` | 训练页主要文字 |
| `NightMuted` | `#9CAC9E` | 训练页辅助文字 |
| `Error` | `#AC3932` | 浅色错误提示 |
| `NightOutline` | `#3C4B40` | 深色表单辅助线 |
| `CardOutline` | `#252525` | 记录编辑输入框线 |

Lime 是 GreenAccent 的别名，不是额外的荧光黄绿色。CardOutline 仅保留给记录编辑输入框，不用于按钮或动作卡片描边。启动窗口使用 Paper；启动图标背景使用 CardFill；原生品牌资源使用 GreenAccent / OrangeAccent。

## 图表和混合色

| 颜色 | 色号 / 规则 | 用途 |
|---|---|---|
| 热度第 2 档 | `#BAE6D8` | Paper 与 GreenAccent 混合 45% |
| 日历容量/有氧热度 | 透明 → GreenTint → 热度第 2 档 → GreenAccent → Green | 无记录到高强度 |
| 三种营养素 | GreenAccent / OrangeAccent / Blue | 蛋白质 / 碳水 / 脂肪 |
| 首页浅底 | Paper 与运动/饮食主色混合 55% | DockGreenTint / DockOrangeTint |
| 普通选中浅底 | Paper 与运动/饮食主色混合 18% | GreenTint / OrangeTint |

混合色号由当前 Compose 在连接手机上实际导出，不使用简单 RGB 平均猜测。

## 分享海报专用颜色

| 色号 | 用途 |
|---|---|
| #FAFCF9 | 照片海报主要文字 |
| #DCE6DF | 照片海报辅助文字 |
| #D5EDE4 | 无照片海报圆形组号底色 |
| #20FFFFFF | 白色单组底色，约 12.5% 透明度 |
| #35FFFFFF | 白色组号底色，约 20.8% 透明度 |
| #B8101814 | #101814 文字保护层，约 72.2% 透明度 |
| #D9101814 | #101814 底部保护层，约 85.1% 透明度 |

八位色号是 Android **#AARRGGBB** 顺序。普通海报已同步 CardFill / FieldFill，普通按钮不使用照片蒙层。

## 首页玻璃与透明度

玻璃使用业务底色、黑色 #000000、白色 #FFFFFF、透明色 #00000000 参与背景折射和光学高光。DOCK 基础 tintAlpha 为 0.18；图标保护区底色强度为浅色 0.78 / 黑色 0.98。光学阴影和高光仅保留于首页，截图像素会随背景和设备改变。完整参数见 GlassStyle.kt、GlassSurface.kt。

## Material 3 全部角色（含备用色）

以下是当前主题的实际运行值。一部分紫色/粉色等是继承的**备用角色**，不表示主界面或普通按钮在使用它们。主要业务控件按上方显式配色绘制；Snackbar、默认错误状态、未覆写的日期选择控件可能使用这些备用角色。

| 角色 | 浅色 | 深色训练主题 |
|---|---|---|
| `primary` | `#23745F` | `#58C7AB` |
| `onPrimary` | `#FFFFFF` | `#101512` |
| `primaryContainer` | `#EADDFF` | `#4F378B` |
| `onPrimaryContainer` | `#21005D` | `#EADDFF` |
| `inversePrimary` | `#D0BCFF` | `#6750A4` |
| `secondary` | `#925009` | `#73B6D5` |
| `onSecondary` | `#FFFFFF` | `#101512` |
| `secondaryContainer` | `#E8DEF8` | `#4A4458` |
| `onSecondaryContainer` | `#1D192B` | `#E8DEF8` |
| `tertiary` | `#7D5260` | `#EFB8C8` |
| `onTertiary` | `#FFFFFF` | `#492532` |
| `tertiaryContainer` | `#FFD8E4` | `#633B48` |
| `onTertiaryContainer` | `#31111D` | `#FFD8E4` |
| `background` | `#FFFFFF` | `#101512` |
| `onBackground` | `#1D2822` | `#F2F5ED` |
| `surface` | `#FFFFFF` | `#202A23` |
| `onSurface` | `#1D2822` | `#F2F5ED` |
| `surfaceVariant` | `#F3F5F6` | `#202A23` |
| `onSurfaceVariant` | `#5F6B62` | `#9CAC9E` |
| `surfaceTint` | `#23745F` | `#58C7AB` |
| `inverseSurface` | `#322F35` | `#E6E0E9` |
| `inverseOnSurface` | `#F5EFF7` | `#322F35` |
| `error` | `#AC3932` | `#F2B8B5` |
| `onError` | `#FFFFFF` | `#601410` |
| `errorContainer` | `#F9DEDC` | `#8C1D18` |
| `onErrorContainer` | `#410E0B` | `#F9DEDC` |
| `outline` | `#DDE3E7` | `#3C4B40` |
| `outlineVariant` | `#DDE3E7` | `#49454F` |
| `scrim` | `#000000` | `#000000` |
| `surfaceBright` | `#FEF7FF` | `#3B383E` |
| `surfaceDim` | `#DED8E1` | `#141218` |
| `surfaceContainer` | `#F3F5F6` | `#211F26` |
| `surfaceContainerHigh` | `#F3F5F6` | `#2B2930` |
| `surfaceContainerHighest` | `#F3F5F6` | `#36343B` |
| `surfaceContainerLow` | `#F3F5F6` | `#1D1B20` |
| `surfaceContainerLowest` | `#FFFFFF` | `#0F0D13` |

## 来源与维护

主主题：ui/theme/Theme.kt；图表：CalendarScreen.kt、MealNutritionRing.kt；首页：HomeDock.kt、ui/glass/；海报：WorkoutPosterRenderer.kt；启动资源：res/values/colors.xml。

UI-COLOR-INVENTORY.json 记录所有显式 UI 色号和源码行位置，不含运行时混合与 Material 备用角色。ui-palette.png 为基础业务色总览。本轮未增加运行时依赖。


## 本版实现与验证

版本 2.1.74 / 85。普通控件修改集中在 AppButtons.kt、AppControls.kt、AppCard.kt、AppSlidingSwitch.kt；筛选/卡片与日期选择涉及 NewTrainingPlanScreen.kt、CalendarScreen.kt、TrainingScheduleScreen.kt、ScheduleCalendarPreview.kt、WorkoutHistoryScreen.kt、FoodComponents.kt、WorkoutRecordEditor.kt；主题及启动色在 Theme.kt、res/values/colors.xml，海报底色在 WorkoutPosterRenderer.kt。

训练周期选择项显示加粗计划名及逐行完整动作名，仍以稳定计划 ID 保存安排。体重、训练历史详情和编辑页将 94% / 92% 高度约束从 ModalBottomSheet 外层移动到内部内容，避免约束缩小锚点计算空间、使抽屉停在顶部并露出底页。未使用覆盖首页的假白色遮挡层，也未保留无效的导航栏颜色补丁。

`:app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline` 通过，Lint 0 错误/2 项既有图标警告；341 项领域检查、3918 项分页/高斯模糊断言和 SQLite 迁移/事务检查通过。在连接的 Android 16 手机上，隔离数据库和偏好的 32 项检查全部通过，包括周期动作竖排顺序、原有返回确认/自动保存/长按拖动/详情滚动/分享编辑和体重面板底部白色像素验证。已目视检查周期列表、筛选按钮、体重抽屉与色板图。

真机截图使用独立测试记录，未改动用户计划、训练历史、偏好或账号。覆盖安装调试版用于验证，测试 APK 验证后卸载；本版尚未发布 releases 安装包。其他 Android 版本和不同导航模式尚未实机覆盖。
