# 2.1.55 / 66 — 用户选择仅保留首页玻璃

用户明确要求撤回全局玻璃按键：生产界面的实时玻璃现在仅用于 HomeDock 的三个入口及左侧共同底座，保留其交互、语义配色和已有 Apache-2.0 算法声明。全局控制迁移到 ui/components/AppButtons.kt、AppControls.kt 和 AppCard.kt，使用实体 Material 表面和轻阴影；纯文字入口无填充框。已移除玻璃弹层、日期选择器、分段按钮、参数面板和筛选/开关的光学装饰，不再为非首页页面采样背景。

白色训练编辑弹层中的黑块是外层玻璃 dark 标记与弹层实际浅色 Material 主题不一致，新实体控制只使用 Material 主题，弹层按自身颜色配置主题。旧版全局材质部分均为历史说明，不代表 2.1.55 的当前效果。日历滑动指标与详细/简洁变化、验证结果详见 FITLOG-2.1.md 的 2.1.55 节。本轮无设备连接，未生成或安装本版包。

# 2.1.54 / 65 — 纠正前景采样与纯色降级

本节覆盖 2.1.53 的“双次绘制”方案：Compose 的子 GraphicsLayer 有缓存，不能先在 recording 状态跳过玻璃前景，再期待同一节点第二遍自然重绘。已改为采样树与玻璃按钮在组成结构上分离，首页独立白色背景 Canvas，普通页保留既有纯背景源；只允许日历网格、训练进度环等纯内容进入采样。移除历史/饮食/周期预览中包裹了玻璃按钮的源，恢复 source.layer 的单次重放，不逐帧截图。

单控件渲染故障不再污染共享源。增加窗口焦点/附着后的能力复查、初始两帧复查、材质模式日志与设置页降级原因。策略仍禁用低能力下的实时折射/模糊；仅保留静态轮廓高光作为近似，用户降低透明度时仍使用实体无光学装饰。Dock 增加通透度并减薄阴影/高光，更深图标保持可读；深色添加沿用浅黑色与白加号。

在已连接 API 36 手机覆盖调试版验证：冷启动进入折射模式，首页设置图标恢复，添加菜单与空白关闭、后续设置点击可用；未改用户外观设置、未清除数据或发布安装包。编译/Lint、领域/SQLite、能力策略和对比度检查通过。白色背景下没有可供观察的细节纹理，模式日志及截图不能替代高对比位移、跨机型和完整性能验收。细节见 FITLOG-2.1.md 的 2.1.54 节。

# 2.1.53 / 64 — 全局按键材质

参考 [Backdrop LiquidButton](https://github.com/Kyant0/AndroidLiquidGlass/blob/kmp/app/src/commonMain/kotlin/com/kyant/backdrop/catalog/components/LiquidButton.kt) 的模糊/透镜分层与弹性反馈，以及 [LiquidToggle](https://github.com/Kyant0/AndroidLiquidGlass/blob/kmp/app/src/commonMain/kotlin/com/kyant/backdrop/catalog/components/LiquidToggle.kt) 的材质分层。本轮新编写 FitLog 原生 Material 适配组件，并继续使用此前固定提交的 Apache-2.0 透镜与高光着色器；没有整库引入、构建版本升级或新增运行时依赖。原版权、完整许可和修改声明继续随包保存。

- 新增 `LiquidButtons.kt`：LiquidButton / LiquidTextButton / LiquidOutlinedButton / LiquidIconButton / LiquidTonalIconButton / LiquidFilterChip / LiquidSwitch / LiquidParameterSurface。保留 Material 控件的 enabled、interactionSource、keyboard/focus、触摸命中与可访问性语义，复用原业务回调；没有添加全屏指针处理。
- CONTROL 材质：2dp 模糊、10dp 折射、8dp 透镜高度、1.5dp 低阴影、窄 3dp 通透光学边缘及 1.4dp 方向高光。文字核心浅色 .97 / 深色 .92 保护底色；前景不参与着色器。按压缩放 .97 并弹簧恢复，减少动态效果时直接切换。
- 全局独立 Button/TextButton/OutlinedButton、图标按钮、主要操作、日历/周期切换、弹窗确认和取消、训练页回退/结束/完成/跳过休息均接入。保留休息灰色、完成绿色及首页添加浅黑色等语义配色。普通返回箭头本轮也采用圆形材质，这是用户本轮“所有按键”试用范围。
- 筛选 Chip、参数数字面板、数字小键盘与开关拇指使用缓存绘制的轻量高光/轮廓近似，不逐项实时采样；这些控件不宣称为真实折射。设置开关继续由原生 Switch 控制点击/拖动和状态。菜单文字列表、可点击内容卡片和日期网格保持内容布局，不把整块列表改成按键。
- 修复 GlassBackdropSource：背景记录阶段仍排除玻璃前景；随后实际绘制页面时执行一次正常内容绘制，不能直接只显示排除了按钮的背景层。显式 Density 快照仍保留；不会在录制阶段递归采样自身。此方案增加有玻璃背景源页面的绘制遍历，滚动性能需要设备测量。
- Android 13+ 在能力允许时使用既有 Backdrop AGSL 背景采样位移；Android 12 为模糊/边缘近似，旧设备或降低透明度/低内存等策略降级为实体。白色背景缺少可折射细节时，视觉主要表现为边缘高光和层次，不能保证与上游彩色背景演示一致。

修改清单：新增 ui/glass/LiquidButtons.kt；调整 GlassStyle.kt、GlassShader.kt、GlassSurface.kt、GlassBackdrop.kt、GlassControls.kt；接入 ui/components/{Components,BodyWeightDialog}、ui/home/HomeScreen、ui/calendar/CalendarScreen、ui/diet/{RecordFoodScreen,DietHistoryScreen}、ui/settings/{SettingsScreen,MiCloudDialog}，以及 ui/workout/{CardioCompletionDialog,CardioInputFields,CurrentExerciseSheet,CompletedTrainingSheet,NewTrainingPlanScreen,ExerciseParameterSheet,PlanSetRows,CustomExerciseSheet,ScheduleCalendarPreview,PlansScreen,TrainingScheduleScreen,TrainingEntryScreen,WeightAdjustmentDialog,WorkoutHistoryScreen,WorkoutRecordDetails,WorkoutRecordEditor,WorkoutSessionScreen,ExerciseInputFields}；更新 app/build.gradle.kts、checks/GlassContrastCheck.py 和项目文档。

验证：341 项领域回归、SQLite 迁移/历史数据保留、45 项菜单策略、84 项能力策略、17 项绘制失败注入通过。对比度检查包含原 356 组和新 90 组按钮文字核心场景，新场景最低 4.58:1。实际触摸、光学观感和帧耗时未做本版真机验证；未打包或安装设备。

## 2.1.43 / 54 — 轻薄材质与卡片统一（源码）

- 通用按钮改为浅绿/浅橙玻璃底与对应强调色文字；深色训练主按钮使用深色玻璃底与浅绿文字。移除非 Dock 控件的模糊外圈光晕，降低阴影和模糊，将文字保护底的宽渐变收窄为 1.5dp，轮廓高光细化至 0.9dp，避免深色中心与透光边缘形成凹陷软边。
- 新增 GlassCard：计划、周期、训练册、动作详情、记录编辑、自定义动作参数、训练准备与饮食历史统一使用米白半透明材质、细方向性轮廓。保留原点击、参数、编辑与删除逻辑，不修改 Dock 手势、导航、计时与训练数据。
- GlassCard 使用 0.95 底色遮盖以保证正文可读，不做逐卡实时折射；这属于同一视觉体系的轻量近似，不宣称为真实折射。浮动按钮与弹层仍使用既有 Backdrop AGSL 折射，白色背景缺少纹理时效果自然克制。降低透明度/旧设备继续实体降级，无新增依赖，已有 Apache-2.0 来源与声明不变。
- 验证：Kotlin 编译、Lint 通过；341 项领域回归、84 项能力/手势策略、45 项菜单边界、17 项绘制降级检查通过；344 项对比度检查通过（新增卡片 20 项，最低 4.58:1）。本版未打包、未覆盖手机；尚未进行实际设备渲染和性能验收。用户“减少动态效果”设置保持。

### 本次源码清单

- 新增：`ui/glass/GlassCard.kt`。
- 统一参数与按钮：`GlassStyle.kt`、`GlassSurface.kt`、`GlassControls.kt`、`WorkoutSessionScreen.kt`。
- 接入卡片：`TrainingEntryScreen.kt`、`CurrentExerciseSheet.kt`、`CustomExerciseSheet.kt`、`FreeTrainingMenuScreen.kt`、`NewTrainingPlanScreen.kt`、`ScheduleCalendarPreview.kt`、`TrainingScheduleScreen.kt`、`WorkoutActionCard.kt`、`WorkoutHistoryScreen.kt`、`WorkoutRecordDetails.kt`、`WorkoutRecordEditor.kt`、`DietHistoryScreen.kt`。
- 校验/版本：`checks/GlassContrastCheck.py`、`app/build.gradle.kts`；交付说明：`README.md`、`docs/FITLOG-2.1.md`、本文件。

## 2.1.42 跨页面材质与命中修复

修复 CalendarScreen 的玻璃分段控件被背景录制跳过；PageScaffold 提供不含前景的白色安全背景源，PrimaryAction 与返回按钮使用统一玻璃表面。GlassTint 对沿轮廓的 SDF 距离连续调整底色透明度，避免旧保护层形成第二个硬边胶囊。BackdropShaders.kt 的新 tint shader 为 FitLog 补充，复用保留版权的上游 SDF；Apache-2.0 许可与来源声明保持。无新增运行时依赖。

真机验证了日历切换控件、训练准备页主按钮及首页点击/全屏菜单滑动；当前 reduce_motion=true 是用户明确保留的设置，选中放大状态依旧可见，展开动画按设置跳过。详情见 FITLOG-2.1.md。

## 2.1.41 材质与 Dock 定位修正

底座胶囊不再使用圆按钮的径向图标保护层，避免中间白斑被误认为高光。新的 protectIcon 参数只用于区分图标圆与共享底座；高光仍沿实际 outline 绘制。标准/强调材质局部边缘晕光扩展至已接入的日历/周期控件、弹窗和训练按钮；材质边缘透明度下调，文字核心独立 .98 遮盖确保可读，300 项对比度检查通过。日期选择器内部实体底色移除。源码保留显式 Density 快照录制与失败降级。

Dock 菜单等待稳定按钮行及自身布局就绪后逐项展开，添加侧固定、入口之间状态隔离；全屏竖向拖动选择首尾钳制并即时反向。详见 FITLOG-2.1.md。此版本只通过编译和检查，本轮未生成 APK，未进行新版本设备视觉验收。

## 2.1.40 真机启动修复

2.1.39 在用户手机启动时因 GraphicsLayer 录制借用实时 DrawScope 密度而触发 CanvasDrawScope.getDensity 递归栈溢出。GlassSurface 与 GlassBackdrop 的录制现全部使用显式 Density 数值快照、方向和尺寸。已在连接的 Android API 36 手机上覆盖安装、冷启动成功，用户确认正常使用。仅此设备的启动修复得到确认，不替代多设备光学与性能验收。

# 2.1.38 / 49 — Dock 与 Backdrop 光学移植

本节覆盖下方 2.1.34 的历史实现说明：旧版独立 AGSL 透镜现已替换为 Backdrop 的 Apache-2.0 圆弧透镜算法，并添加其方向性法线高光。并非仅调整模糊参数，也不是引入整个 Backdrop 依赖。

## GitHub 插件调研与选择（2026-10-14）

- 选择 [Kyant0/AndroidLiquidGlass (Backdrop)](https://github.com/Kyant0/AndroidLiquidGlass)：Compose 专用，提供透镜、高光、阴影、背景采样与组件示例。调研时约 4153 stars，默认 kmp 分支，最近推送 2026-08-26；星标不是稳定性保证。已读取实际 LICENSE、版本目录、透镜与高光源码。
- 固定源码提交 `65ab177e90e5c1d8c62e70cf7755841982da65f6`。移植 `backdrop/src/commonMain/kotlin/com/kyant/backdrop/internal/Shaders.kt` 的 RoundedRectSDF、RoundedRectRefractionShaderString、DefaultHighlightShaderString；保留版权头，注明提取和适配修改。通过 Android RuntimeShader/ShaderBrush 和现有 Compose GraphicsLayer 接入。
- 当前上游版本目录使用 Kotlin 2.4.10、Compose 1.12.0、AGP 9.3.2，与本项目差距明显。因此采用小范围源码移植，没有升级构建环境，没有增加大型或第三方运行时依赖。
- 比较 [QWEA0/Liquid-Glass-Android](https://github.com/QWEA0/Liquid-Glass-Android)：面向 Android View/XML，README 也建议 Compose 项目使用 Kyant0，故不添加 AndroidView 桥接。README 标注 MIT，但未复制它的代码。
- 比较 [Mortd3kay/liquid-glass-android](https://github.com/Mortd3kay/liquid-glass-android)：Compose/AGSL，约 187 stars、最近推送 2025-12-08，API 33+；GitHub 许可证识别为 Other/NOASSERTION，本轮不复制。
- Apache-2.0 全文在 `docs/third-party/Backdrop-LICENSE.txt` 与随包分发的 `app/src/main/assets/licenses/Backdrop-LICENSE.txt`，修改归属声明位于同目录 Backdrop-NOTICE.txt。

## 效果与交互

- Dock 保持左侧锻炼/饮食同底座，添加独立；只显示 28dp 图标，读屏仍保留文字名称与展开/收起状态。菜单与竖线锚点仍来自对应圆圈。
- 激活材质 1.08、图标 1.18，按压材质 0.96；加号随添加菜单状态旋转 45°，再次点击关闭。减少动态效果时省略动画，旋转直接显示最终状态，方便辨认。
- 新 DOCK 材质边缘底色 alpha 0.18、模糊 2dp、折射 12dp、边缘深度 12dp；背景像素沿上游 circleMap 圆弧透镜偏移，按压增加 12% 折射。标准文字材质仍使用原保护底色，避免透底影响正文。
- 图标核心以径向保护底色确保对比度，边缘保持清晰折射，避免旧版多次叠色将整个表面变成近乎实体。圆内未加入图片或纹理。
- 单独绘制法线方向性白色高光、暗色接触轮廓、局部柔和晕光与空间阴影；不模糊或扭曲文字/图标。光晕只用于 Dock 浮动控件，非满屏装饰。
- API 33+ 使用上游 AGSL 真实采样位移；API 31–32 仅背景模糊及渐变边缘近似；低版本/降低透明度/低内存等条件维持实体降级，无光晕和折射。白色背景下缺少可被折射的细节，不能虚构背景纹理。
- 首页背景采样改为包含 Dock 下方白色留白的完整内容层；Dock 仍是它的兄弟节点，排除前景，避免原先从短正文图层之外取得透明像素。
- 无持续光照或传感器监听，只在既有输入/状态变化时更新；半透明背景层不包含玻璃前景，防止递归采样。

## 修改文件

HomeScreen.kt、HomeDock.kt、GlassStyle.kt、GlassShader.kt、GlassSurface.kt、新增 BackdropShaders.kt、debug GlassOpticsPreview.kt、版本文件、README、本文档、Backdrop 许可证和归属声明；对比度检查扩展至 Dock 图标核心。

## 验证边界

本轮实际通过：最终 Kotlin 编译、Lint；341 项领域回归、84 项材质能力与菜单策略、16 项动作转场检查；120 项原有文字核心及 180 项 Dock 图标对比度检查（覆盖放大、按压、黑白与灰背景，最低分别 4.63:1、4.66:1）。

尚无连接设备，未验证实际 GPU 着色器画面、手机交互与帧耗时。Kotlin 编译成功不等于运行时 AGSL/光晕视觉验收通过；debug 格网预览用于后续真机对比实际边缘位移。未生成安装包。

---

# FitLog 2.1.34 — 原生 Liquid Glass

本次为现有 Android Kotlin/Jetpack Compose App 的局部材质升级，不引入 React、WebView、Flutter 或新的运行时依赖，不更换 Kotlin、Compose BOM、AGP、compileSdk 和 minSdk。

## 实现与边界

- `GlassBackdropHost` 提供同一窗口的背景层。`glassBackdropSource` **只标记不含玻璃控件的内容区域**：首页正文、训练环、训练册/饮食历史列表、饮食表单、日历网格、周期日期条及周期日历。记录区域的屏幕坐标，前景重用 GPU GraphicsLayer；不捕获整个含玻璃前景的 NavHost，避免缓存 RenderNode 递归采样。未标记的内容和系统窗口不在采样范围内。
- Android 13+ 的 `GlassShader` 使用 RuntimeShader/AGSL 对实际背景采样坐标进行位移。圆/圆角矩形的距离场确定边缘和法线，边缘带向内偏移采样，中央不位移。模糊与折射都是背景层效果，文字、图标另行绘制，不参与扭曲。模糊不能称为折射；此实现也不是 Apple 的系统材质 API。
- 材质有 LIGHT、STANDARD、EMPHASIZED 三档。透明度、模糊、边缘带、折射强度和阴影集中在 GlassStyle。中心有保护性底色，边缘保留半透明效果；深色高光更弱。阴影使用原生 graphicsLayer，路径与 RenderEffect 按几何缓存。
- 白色页面没有纹理时，折射天然不显眼；不使用假纹理、图片或复杂渐变来夸大效果。高对比测试背景只在 debug Preview 中存在。
- 弹窗通过现有 Material AlertDialog、ModalBottomSheet 和 DatePickerDialog 添加材质，不重做表单布局、焦点、保存、确认或导航。打开时对应用标记背景进行一次最长边 1024px 的 GPU 缩放/内存快照；旋转后重取，关闭释放，不写入磁盘。弹窗背景不是逐帧实时更新，未取到快照即使用实体材质。

## 已接入的界面

- 首页：成组的锻炼/饮食底座、三个圆入口。布局、菜单归属、向上文字列表、单手操作位置保持。按下材质压缩至 0.96；展开后图标放大至 1.18，文字不受材质缩放。菜单展开使用有界弹簧，原长按/拖动/松手取消与导航保留。
- 训练页：顶部回退/结束与完成、跳过休息、保存主按钮；训练参数、重量和次数等弹窗。环、水槽、数据布局及业务逻辑保持。
- 日历运动/饮食、力量/有氧切换；周期按天/按周模式及周/月视图切换；月份和日期选择器。
- 训练详情、历史编辑、饮食详情和相关参数/确认弹窗。记录列表、普通卡片、输入框仍为实体材质，没有给每张列表卡增加实时折射。

## 能力与无障碍

| 条件 | 实际材质 |
| --- | --- |
| API 33+、硬件加速、正常设备状态 | 背景折射＋模糊＋边缘高光 |
| API 31–32 | 背景模糊近似；无真实折射 |
| API 26–30、低内存、节电、严重热限制、软件渲染、降低透明度 | 实体降级，交互不变 |
| 背景未就绪、跨窗口无快照、渲染失败 | 实体降级；不伪造背景 |

设置增加“减少动态效果”“降低透明度”，旧偏好默认 false，无数据库迁移。观察公开系统动画时长、节电广播、前台恢复和热状态；系统关闭动画时减少动态效果。Android 厂商降低透明度没有统一公开接口，因此提供应用内开关，不读取私有设置。

减少动态效果关闭玻璃弹性、追光移动和训练水波循环，保留状态变化。降低透明度关闭背景记录、模糊及折射。首页入口支持 Enter/Space；菜单支持上下方向键、Enter/Space 确认、Escape 收起，保留触摸与读屏描述。文字核心区域的代表性配色在黑/白极端背景下进行 WCAG 对比度检查；不是逐帧像素采样。

## 开源研究与归属

以下是**参考项目，不是应用依赖**。本次 Kotlin/AGSL 实现自主编写，没有复制其 TypeScript、SVG、GLSL 或组件源文件，因此不能把它们列为已安装库。MIT 许可证全文另存于 `third-party/glass-references`，用于记录研究时的许可。

| 项目 | 核实许可证 | 采用/未采用 |
| --- | --- | --- |
| [leefanv/liqui-design](https://github.com/leefanv/liqui-design) | MIT | 参考边缘折射、集中材质参数与阅读保护思路；未安装 React 包 |
| [runvendo/fluidkit](https://github.com/runvendo/fluidkit) | MIT | 参考材质/文字分层及弹簧反馈；未安装 Motion 或 GPU 包 |
| [aberhamm/liquid-glass-react](https://github.com/aberhamm/liquid-glass-react) | MIT | 研究浏览器降级，未直接采用 |
| [CruxGarden/plasma-ui](https://github.com/CruxGarden/plasma-ui) | MIT | 研究 WebGL 材质，未直接采用 |
| [zsio/liquid-glass](https://github.com/zsio/liquid-glass) | 未找到可核实的明确许可证 | 未复制代码 |
| [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) | Apache-2.0 | 研究原生背景分层；当前构建栈更高，不作为依赖，也未移植源文件 |

原生 API 参考：[Compose GraphicsLayer](https://developer.android.com/develop/ui/compose/graphics/draw/modifiers)、[RuntimeShader / RenderEffect](https://developer.android.com/develop/ui/views/graphics/agsl/using-agsl)。

## 测试与验收

- 已执行 `:app:compileDebugKotlin :app:lintDebug --offline`，BUILD SUCCESSFUL；最终源码再次检查通过。`WorkoutEngineCheck` 原有 341 项业务检查全部通过，GlassPolicyCheck 的 84 项及 GlassContrastCheck 的 120 项也全部通过。
- `GlassPolicyCheck` 覆盖 84 项 API 能力分级、低内存/节电/热/错误降级、键盘顺序及松手取消/确认策略。
- `GlassContrastCheck.py` 检查 120 项材质核心配色，黑/白背景、三档透明度及最大高光；最低 4.63:1。该结果覆盖检查中的文字核心，不等同于全界面截图验收。
- `src/debug/.../GlassOpticsPreview.kt` 使用生产组件和棋盘背景验证三档轮廓、真实边缘位移与 API 26 实体降级。未注册到产品导航，不进入 release 源集；Layoutlib 可能降级，真实折射需硬件 API 33+ 设备验收。
- 当前没有 Android 模拟器运行环境，也没有连接手机。**尚未完成** API 26/31/33 实机截图、长按拖动/鼠标键盘完整交互、帧耗时、GPU 着色器画面和内存压力验收，不宣称零掉帧或已验证实际折射图像。
- 待真机检查：小屏/大字体/键盘/安全区、点击与长按、滑回取消、多指取消、系统返回、旋转与前后台、亮暗背景下阅读，以及训练记录、饮食记录和历史编辑保存。

用户本轮明确要求生成安装包，已生成 releases/fitlog-2.1.34.apk（2.1.34 / 45）。assembleDebug 与签名验证通过，签名与 2.1.32 相同，可覆盖安装。真机视觉与性能验收仍待完成；后续改动继续等待用户明确要求打包。

## 修改与新增文件清单

- `app/build.gradle.kts`
- `app/src/debug/java/com/fitlog/app/ui/glass/GlassOpticsPreview.kt`
- `app/src/main/java/com/fitlog/app/data/SettingsRepository.kt`
- `app/src/main/java/com/fitlog/app/domain/GlassPolicy.kt`
- `app/src/main/java/com/fitlog/app/ui/AppNavHost.kt`
- `app/src/main/java/com/fitlog/app/ui/calendar/CalendarScreen.kt`
- `app/src/main/java/com/fitlog/app/ui/components/BodyWeightDialog.kt`
- `app/src/main/java/com/fitlog/app/ui/components/Components.kt`
- `app/src/main/java/com/fitlog/app/ui/diet/DietHistoryScreen.kt`
- `app/src/main/java/com/fitlog/app/ui/diet/RecordFoodScreen.kt`
- `app/src/main/java/com/fitlog/app/ui/glass/GlassBackdrop.kt`
- `app/src/main/java/com/fitlog/app/ui/glass/GlassControls.kt`
- `app/src/main/java/com/fitlog/app/ui/glass/GlassEnvironment.kt`
- `app/src/main/java/com/fitlog/app/ui/glass/GlassShader.kt`
- `app/src/main/java/com/fitlog/app/ui/glass/GlassStyle.kt`
- `app/src/main/java/com/fitlog/app/ui/glass/GlassSurface.kt`
- `app/src/main/java/com/fitlog/app/ui/home/HomeDock.kt`
- `app/src/main/java/com/fitlog/app/ui/home/HomeScreen.kt`
- `app/src/main/java/com/fitlog/app/ui/settings/MiCloudDialog.kt`
- `app/src/main/java/com/fitlog/app/ui/settings/SettingsScreen.kt`
- `app/src/main/java/com/fitlog/app/ui/workout/CardioCompletionDialog.kt`
- `app/src/main/java/com/fitlog/app/ui/workout/CurrentExerciseSheet.kt`
- `app/src/main/java/com/fitlog/app/ui/workout/CustomExerciseSheet.kt`
- `app/src/main/java/com/fitlog/app/ui/workout/ExerciseParameterSheet.kt`
- `app/src/main/java/com/fitlog/app/ui/workout/ScheduleCalendarPreview.kt`
- `app/src/main/java/com/fitlog/app/ui/workout/TrainingScheduleScreen.kt`
- `app/src/main/java/com/fitlog/app/ui/workout/WeightAdjustmentDialog.kt`
- `app/src/main/java/com/fitlog/app/ui/workout/WorkoutHistoryScreen.kt`
- `app/src/main/java/com/fitlog/app/ui/workout/WorkoutRecordDetails.kt`
- `app/src/main/java/com/fitlog/app/ui/workout/WorkoutRecordEditor.kt`
- `app/src/main/java/com/fitlog/app/ui/workout/WorkoutSessionScreen.kt`
- `checks/GlassContrastCheck.py`
- `checks/GlassPolicyCheck.java`
- `docs/FITLOG-2.1.md`
- `docs/LIQUID-GLASS.md`
- `docs/third-party/glass-references/fluidkit-LICENSE.txt`
- `docs/third-party/glass-references/liqui-design-LICENSE.txt`
- `docs/third-party/glass-references/liquid-glass-react-LICENSE.txt`
- `docs/third-party/glass-references/plasma-ui-LICENSE.txt`
- `README.md`



