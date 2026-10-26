# 动作讲解原生播放

当前源码版本：2.1.84 / 95，原生播放最初接入版本2.1.75 / 86。讲解按钮在训练详情内打开原生播放器，关闭后返回原来的训练详情，保留未保存的动作参数。未知动作没有视频链接时仍提供搜索入口。

## 实现

- `domain/BiliVideoLink.kt` 校验 BV/AV、分 P、分享链接和媒体域名。
- `data/video/BilibiliVideoClient.kt` 读取公开视频信息，解析分 P 与匿名可访问的 AVC/AAC 流；仅跟随受信任的分享跳转，媒体仅使用 B站 CDN。支持接口返回的备用地址，拒绝试看流。
- `ui/workout/TutorialVideoDialog.kt` 使用 Media3 原生解码和控制器，支持进度、暂停、倍速、分 P、全屏、重试。系统返回先退出全屏，再返回训练；后台暂停，关闭释放播放器。
- `ui/workout/CurrentExerciseSheet.kt` 接入原讲解按钮。

不加载移动网页，不自动跳浏览器，不保存账号、Cookie 或视频文件。原视频入口由用户主动点击。网络、下架、登录限制和超时显示错误与重试；不会绕过付费、地区或登录限制。训练计时与原训练页面逻辑保持。

普通页面固定竖屏，只有视频全屏允许横屏：按实际视频宽高识别横向内容，进入后请求传感器横屏（可左右两侧横放），竖向视频保持竖屏。播放窗口匹配整个屏幕、隐藏系统栏并处理刘海区域；PlayerView以FIT保持视频比例，比例不同允许必要黑边，不裁切或拉伸画面。退出全屏或关闭播放恢复竖屏。Activity处理方向/尺寸配置变化，方向切换不重建训练页面或播放器，保留草稿、播放进度及倍速。

公开接口并非稳定的第三方开发者契约，可能随 B站策略变化而失效；匿名清晰度取决于平台提供的流，优先兼容 AVC，最高请求 720P。折叠屏、多窗口及平板系统可能有独立方向策略，本轮以已连接手机为验收对象。

## 依赖和来源

新增 `androidx.media3:media3-exoplayer:1.4.1`、`androidx.media3:media3-ui:1.4.1`，Apache-2.0。选择兼容 compileSdk 34 的版本，未升级 Kotlin、Compose 或 Android 构建栈。随包保留 Apache 许可证。

- AndroidX Media3：https://github.com/androidx/media
- 官方使用文档：https://developer.android.com/media/media3/exoplayer/hello-world
- 版本说明：https://developer.android.com/jetpack/androidx/releases/media3#1.4.1
- 调研参考：https://github.com/yearzen1/bilibili-pure-apk （MIT）。参考原生播放方向，FitLog 的链接校验、解析和播放窗口独立实现，没有引入该项目依赖或复制其整套业务实现。

国内网络无法访问 Google Maven 时，可选择 `-Pfitlog.mavenMirror=true` 使用 Aliyun 的 Google/Central 镜像；默认仍为官方仓库。

## 验证入口

2026-10-20，2.1.84 / 95：编译、Lint及本地回归通过；Android16手机播放专项27项通过。用实际1920×1080横向教程验证解码、暂停、进度、倍速、全屏横向播放、系统返回恢复竖屏、播放器及宿主不重建；测试还将设备旋转到横向，确认普通页面保持竖屏。全屏窗口宽度按物理屏幕2414像素断言，截图确认不再露出两侧灰边。Compose1.7弹窗使用显式窗口尺寸与原生测量路径，避开screenWidthDp对刘海安全区的截短。已覆盖安装调试版，用户训练记录未修改；测试结束恢复原系统旋转设置并移除测试组件。未发布安装包。

- `checks/BiliVideoCheck.java`：32 项链接、分 P 和 CDN 校验。
- `TutorialVideoValidation`：隔离测试页面中的解析、错误/超时、实际网络解码、暂停、进度、倍速、全屏和返回，不访问训练数据库。
- `WorkoutSaveValidation`：原训练保存、菜单手势、周期及抽屉回归。

播放真机测试构建参数：`-Pfitlog.testRunner=com.fitlog.app.TutorialVideoValidation`；默认仍使用训练回归 runner。测试 APK 仅供本机验证，不作为发布内容。

2026-10-19 Android 16 真机结果：播放专项 20 项通过，公开卧推教程实际渲染出画面，暂停、跳进度、倍速和全屏返回通过。修复网页流 `platform=pc` 与移动端 UA 不匹配造成的 CDN 403；媒体请求使用桌面浏览器 UA，保留 Referer 与平台提供的备用地址。没有修改签名地址或绕过视频访问权限。

Kotlin 编译通过；Lint 0 错误、2 条原有未使用图标警告；341 项领域、32 项视频链接、3918 项分享分页/高斯模糊及数据库回归通过。Media3 1.4.1 的多段渐进式兼容路径使用已弃用但仍可用的 `ConcatenatingMediaSource`，不影响本次单段 DASH 播放。Android 8/12 等其他设备尚未实测。

原有 Android 16 真机训练回归 32 项通过，用户数据库未改动。已覆盖安装 2.1.75 调试版并移除测试组件；本轮没有发布下载包。
