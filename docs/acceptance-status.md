# QingJi v5 验收记录

更新日期：2026-11-05（Asia/Shanghai）

## 构建与自动化检查

- 命令：`.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`
- 结果：`BUILD SUCCESSFUL`。
- JVM 单元测试：61 项通过、0 失败、0 错误、0 跳过（DateUtils 14、ExpenseAnalytics 15、MoneyUtils 32）。
- Android lint：通过，报告 7 条非阻断警告（6 条依赖版本建议、1 条自适应图标 mipmap qualifier 警告）。
- Gradle 另会对 `android.overridePathCheck=true` 打印一条实验性选项提示；它用于当前 Windows Unicode 路径兼容。
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，10,826,341 bytes。
- SHA-256：`403CDB3897ACE64B9A8196E9573549C691590F4293C31A263C3290E5C3D9F928`。

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
