# LiftingLoop

[English](README.md) · [简体中文](README.zh-CN.md)

**把训练、饮食和每一次进步，认真记录下来。**

LiftingLoop 是一款面向 Android 的原生健身记录 App，由 DAVID DAI 设计，借助 OpenAI Codex 进行 vibe coding 开发。它把力量训练、有氧、饮食记录与训练日历放在一起，注重训练过程中的操作手感，也希望让回看和分享成绩变得简单。

使用 Kotlin、Jetpack Compose 和 Room 构建，支持 **Android 8.0 及以上**。当前版本为 **beta1.1.1（versionCode 98）**，仍在持续迭代。

## 特色功能

- **细到每一组的力量训练**：自定义训练计划、自由训练、动作搜索与管理；支持器械、自重、额外负重和逐组参数，方便记录递减组。动作参数会被记住，用于后续训练。
- **训练中随时调整与回退**：调整重量、次数、组数和组间休息；最近操作可撤销，训练进度与撤销状态持久保存，支持中断后继续。
- **有氧也有自己的流程**：连续与间歇训练，支持计时、暂停，以及结束时确认实际时长、距离等数据。
- **跟着自己的节奏安排训练**：训练计划与训练周期分开管理，支持按天循环或按周安排，并提供接着上次练或按今天安排练的选择。
- **看得见的训练记录**：月历与周历、训练容量热度、每次训练的逐组明细，以及本周训练天数；支持查看和编辑历史记录。
- **饮食记录与营养概览**：内置参考食物库，支持搜索、分类、多选、自定义食物与标签、份量调整；展示热量和三大营养素供能占比，未知营养明确标注。
- **把成绩做成照片海报**：拍照或选图，拖动、缩放照片并调整文字排版；支持保存图片和通过系统分享面板分享。
- **有辨识度的原生界面**：薄荷绿运动、暖橙饮食、深色训练界面，首页玻璃入口、休息水位动画与训练完成动效；提供减少动态效果和降低透明度设置。
- **本地记录，按需联网**：核心训练和饮食记录保存在本机；动作讲解视频与可选的实验性米家体重同步需要网络。

## 关于 open gym

> 本项目可能部分功能与GitHub上很火的另一个项目open gym有雷同，但是我发誓我在开始vibe coding 这个app的时候还不知道open gym。open gym是一款更优秀的软件，在前几天我看到open gym这个项目的时候都震惊了。ta简直把我想象中的功能做到了极致。

——作者的话

## 安装与版本

请查看本仓库的 [GitHub Releases](https://github.com/www2733659262-source/LiftingLoop-Workout-Log/releases) 获取已发布安装包；若尚无 Release，可按下文自行构建。APK 不存入源码 Git 历史。

当前本地分发版本信息见 [beta1.1.1 安装包说明](releases/fitlog-beta1.1.1.txt)。目前安装包使用 debug 签名；自行构建时的签名可能不同，覆盖安装前请确认签名与数据保留需求。

## 从源码构建

需要 JDK 17、Android SDK 34 和 Gradle 8.7。也可以使用 Android Studio 打开项目，配置 Gradle JDK 为 17，并安装 SDK 34。

1. 克隆仓库并打开项目目录。
2. 通过 Android Studio 设置 SDK 路径，或创建不提交到 Git 的 `local.properties`，写入本机的 `sdk.dir`。
3. 本仓库当前未包含完整 Gradle Wrapper，请安装 Gradle 8.7，并确保 `gradle` 位于 PATH。
4. 执行：

```sh
gradle :app:assembleDebug :app:lintDebug --console=plain
```

生成的 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。首次构建需要联网下载依赖。主要依赖版本集中在 [libs.versions.toml](gradle/libs.versions.toml)。

本地调试签名可放在 `.local/debug.keystore`；没有该文件时使用 Android 默认 debug 配置。签名文件、本地 SDK 配置、缓存和构建产物已列入 `.gitignore`。

## 项目结构

| 目录 | 内容 |
| --- | --- |
| `app/src/main/java/com/fitlog/app/ui` | Compose 页面、训练交互与视觉组件 |
| `app/src/main/java/com/fitlog/app/domain` | 训练、有氧、周期、营养等领域逻辑 |
| `app/src/main/java/com/fitlog/app/data` | Room 数据库、本地存储与可选网络集成 |
| `checks` | 领域逻辑、数据迁移、食物库等校验脚本 |
| `docs` | 功能说明、设计记录与第三方来源 |

## 功能边界

- 米家 S200 云端体重同步为实验功能，尚未完成真实账号与设备的完整端到端验证，不能承诺所有设备、地区或后续接口版本可用。
- 食物库包含历史参考菜单和估算值，部分营养信息缺失，不代表品牌完整现售菜单；可按实际包装和份量手动校正。
- 动作讲解视频依赖第三方服务和原作者资源，可用性可能变化。
- 自动食物识别尚未提供；饮食记录使用内置参考库或手动填写。
- 当前为 beta 软件，设备适配、交互和数据恢复仍需持续验证。发布前请在目标设备测试。

## 开发与参与

欢迎通过 Issues 报告问题或提出想法，也欢迎提交 Pull Request。反馈问题时请附 Android 版本、App 版本、复现步骤和预期结果；分享截图与日志前，请移除账号凭据和私人记录。

构建后可参考 [checks/verify.ps1](checks/verify.ps1) 运行已有检查；该脚本默认依赖项目相邻的本地 `toolchain` 目录，并非通用 CI 配置。

更多细节：[有氧训练](docs/CARDIO.md) · [分享编辑器](docs/WORKOUT-SHARING.md) · [动作管理](docs/EXERCISE-MANAGEMENT.md) · [训练安排接续](docs/SCHEDULE-CATCH-UP.md) · [饮食数据来源](docs/FOOD-CATALOG.md) · [米家同步](docs/MIJIA-SYNC.md)。

原始迭代记录保存在 [开发历史](docs/DEVELOPMENT-HISTORY.md)，其中的本地安装路径与历史验收结论仅作为开发档案。

## 许可与致谢

LiftingLoop 原创代码采用 [Apache License 2.0](LICENSE)。第三方代码、图片、品牌和视频保留各自权利，具体来源与许可边界见 [第三方声明](THIRD_PARTY_NOTICES.md) 和 [NOTICE](NOTICE)。部分参考图素材尚需补充完整授权信息，请勿将代码许可视为所有素材的使用许可。

Design by **DAVID DAI** · 开发辅助：**OpenAI Codex** · 设计辅助：**Autodesk AutoCAD 2022**
