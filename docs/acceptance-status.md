# QingJi v5 验收记录

更新日期：2026-11-05（Asia/Shanghai）

## 构建与自动化检查

- 命令：`.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`
- 结果：`BUILD SUCCESSFUL`。
- JVM 单元测试：61 项通过、0 失败、0 错误、0 跳过（DateUtils 14、ExpenseAnalytics 15、MoneyUtils 32）。
- Android lint：通过，报告 7 条非阻断警告（6 条依赖版本建议、1 条自适应图标 mipmap qualifier 警告）。
- Gradle 另会对 `android.overridePathCheck=true` 打印一条实验性选项提示；它用于当前 Windows Unicode 路径兼容。
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，10,826,341 bytes。
- SHA-256：`17406ACC7C5FF4C6B163A91CC8216A41ECCC87D576E25D61CD748B8F94F484EB`。

构建和测试已在本轮最终源码状态下完成。Windows 中文目录下的 Gradle 测试工作进程有路径兼容问题，因此通过同一源码目录的 ASCII junction 执行；APK 来自此构建。

## 真机验收

设备：`25057RA09C`，Android 16 / API 36，1080 × 2340、450 dpi。正常字体比例为 1.0。另用 360 dp 宽度和 1.3 字体比例检查窄屏与放大文字。

- 已安装最终 Debug APK，检查首页、账单、统计、设置、详情和记账表单的深浅主题展示。
- 已新增一条带独立 QA 标记的流水，验证成功反馈与首页/统计刷新；随后编辑金额并跨月改日期，验证月度汇总和趋势更新。
- 已验证备注搜索、分类与月份组合筛选，以及“没有匹配流水”的空状态；搜索条件清除后列表恢复。
- 已打开删除确认并取消，随后只删除本轮创建的 QA 流水。设备原有流水数量和汇总在测试前后保持一致；具体金额不记录在报告中。
- 已测试日期选择器、未来日期禁选、键盘弹出时保存按钮可见、返回未保存表单时的确认与放弃。
- 360 dp / 1.3 字体比例下，首页总额和类别行保持可读，记账表单保存操作仍可见，设置页可滚动。
- 已强制停止并重启应用；SQLite 流水和本地偏好恢复。当前已恢复为跟随系统主题、金额显示、默认分类“餐饮”。设备屏幕尺寸与字体比例也已恢复原值。

真机截图和 UI hierarchy 保存在本地 `docs/device-acceptance/`，其中含私人消费信息，已由 `.gitignore` 排除，不上传到公开仓库。PC 设计参考截图也保存在本地并排除。

## 验收边界

- 61 项自动化检查是日期、金额格式、筛选和统计工具的 JVM 单元测试；目前没有自动化 ViewModel/SQLite 端到端套件。
- 真机验收覆盖一台 Android 16 设备和有限的屏幕/字体组合；未覆盖 Android 8–15 机型矩阵、TalkBack 深度操作、OEM 备份/换机恢复行为。
- 最终连续演示视频尚未录制。录制流程和 HR 讲解要点见[演示提纲](demo-and-hr-brief.md)。
- 本记录验证的是最终 Debug APK，不代表签名 Release 包或商店分发验收。

## v6 集成检查点（2026-11-05，`0d60b03`，收尾完成，剩余待办见下）

日期：2026-11-05（Asia/Shanghai）

源码基线：`codex/qingji-v6-integration`，HEAD `0d60b03`（`d49b52f` + `62d075c` cherry-pick + `0d60b03` a11y 收尾）。

本轮变更：
- `62d075c`：将暂停分支 `codex/qingji-v6-consumer-polish`（`8e13d42`）cherry-pick 进集成分支——统一首页/统计/详情在「隐藏金额」下的 TalkBack 描述为「金额已隐藏」（掩码字符 `¥••••` 仅作为可视文本，读屏读「金额已隐藏」、不朗读掩码、不泄露真实金额）；详情页删除期间显示进度与「正在删除…」并禁用重复操作，ViewModel 层有 in-flight 防重入。
- `0d60b03`：统计页月总额的读屏描述由「消费总额」统一为「金额已隐藏」（与其余页面一致）。
- `021c53c` + `237ace2`（来回）：曾尝试按 lint 提示把 `mipmap-anydpi-v26` 并入 `mipmap-anydpi`，构建在资源链接阶段失败（`<adaptive-icon>` 必须使用 `-v26` 限定符），已整体回退；该 ObsoleteSdkInt 警告为自适应图标的误报，保留并在下方说明。

构建与自动化：
- 命令：`.\gradlew.bat --no-daemon :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:compileDebugAndroidTestKotlin`（JDK 23 `D:\Java\jdk-23`，经 junction `D:\QingJiV6Integration`）。
- 结果：`BUILD SUCCESSFUL`，退出码 0；JVM 单测 108 项通过、0 失败、0 错误、0 跳过（12 个测试类）；`:app:compileDebugAndroidTestKotlin` 通过。
- Android lint：0 错误、10 条警告——9 条 `GradleDependency` 依赖版本建议（按本轮边界暂不升级 AndroidX，避免回归风险）；1 条 `ObsoleteSdkInt`（`mipmap-anydpi-v26`，经构建验证为自适应图标误报，未处理）。
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，11,361,620 bytes。
- SHA-256：`b88916de51d6bd1d8c8a400d1a4d7c158fae892e51ee27ba446fbac4bf1905a4`。

设备验收（Redmi `25057RA09C`，Android 16 / API 36，已 `install -r` 覆盖安装，未清数据）：
- 原 690 笔虚构流水与 11 个月预算在更新后完整保留（首页 `¥1,208.29 / 14 笔`，账单 690 笔）。
- 金额隐私：开启「隐藏金额」后，首页总额/对比/趋势/分类、统计页总额/分类、详情页金额的读屏描述均为「金额已隐藏」，界面文本为 `¥••••`，未发现真实金额出现在文本或描述中；验证后已关闭该开关。
- 深浅主题：深色（深绿背景、白字）与浅色（白底、绿/橙/灰）均正常，已恢复「跟随系统」。
- 删除反馈：新建唯一标记临时记录 → 保存（691 笔）→ 删除确认弹窗文案正确 → 删除后回落 690 笔，today 计数同步；「正在删除…」进度态因删除事务过快未能在连拍中截获，已通过代码审查确认（`isDeleting` 分支 + 按钮禁用 + `deleteInFlight` 防重入）。
- 持久化：多次覆盖安装与 `force-stop` 后重启，记录数与金额、偏好均恢复。
- 此前的账单/日历/预算/图表联动（趋势选日→账单、分类点选→流水、日历选日→当日、编辑预算→剩余/进度更新、搜索/月份/分类组合筛选）已在灌库当轮真机验证通过。

仍未完成（明确保留为交付待办，不虚报）：
- 60–90 秒连续真机录屏：**未完成**，未生成演示视频。
- Android instrumentation（`:app:connectedDebugAndroidTest`）：设备策略限制安装测试 APK（历史 `INSTALL_FAILED_USER_RESTRICTED`），实际执行仍为 0 项，**未验证**。
- SAF 导出/恢复的完整实机往返：未在本轮实机验证（代码已接线）。
- 「演示模式 / 示例账本」的可选体验：未实现；当前示例数据为直接写入设备数据库的虚构数据，无应用内一键载入/退出机制。
- GitHub 远程：本地 `codex/qingji-v6-integration` 尚未推送；远程 `main` 仍为 `27a8bb1`，与本分支存在差异，未做任何推送或强制覆盖。

上述结果证明当前 HEAD 可完整构建、lint 通过、108 个 JVM 单测通过且 AndroidTest Kotlin 可编译，且已完成上述真机复核项；整体产品验收（录屏、instrumentation、SAF 往返）仍为进行中状态。
