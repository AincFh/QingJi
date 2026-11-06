# 轻记 QingJi

轻记是一款面向个人日常消费的原生 Android 记账原型。核心流程是快速记下一笔、回看历史流水，并从月度汇总、类别占比和近期趋势中了解消费去向。

## 产品与设计

- 原生 Kotlin、Jetpack Compose、Material 3 与 SQLite；数据默认只保存在本机。
- 四个底部目的地：首页、账单、统计、设置；“记一笔”作为全局主操作放在底部中央。
- 视觉使用暖纸白、深松绿与柿橙，深色模式切换到石墨色阶；强调清晰层级、内容优先和克制的容器。
- 保留 Android 导航、日期选择、输入法和系统主题行为，同时参考 Apple HIG 的清晰层级与可读性原则。

## 功能

- 记录、编辑、查看和确认删除日常支出；金额以整数分保存。
- 首页按月份展示支出总额、笔数、七日趋势、分类结构和近期流水。
- 账单支持全历史搜索、分类筛选和月份筛选，组合条件共同作用。
- 统计展示分类金额与占比、七日或三十日趋势，以及与上月同期的比较。
- 设置支持跟随系统/浅色/深色、金额隐私显示和新记录默认分类。
- 新增和编辑共用表单；键盘、无效金额、未保存返回和删除操作都有明确反馈。

当前版本聚焦单人、本机、日常支出记录。v6 集成分支新增月预算及手动 JSON 备份/恢复；此次增补 **CSV 账单只读导出**。收入记账、云同步、多人账本和 AI 对话仍不在此版本范围内。

## 账单 CSV 导出（v6 集成候选）

在 **设置 → 数据与存储 → 导出账单 CSV** 打开 Android 系统文件保存器，手动选择路径并保存为 `qingji_expenses_年_月_日.csv`。

- 内容：日期（ISO 8601）、分类、金额（人民币元，固定两位小数）、备注；按消费日期和创建时间从新到旧排序。无账号、设备 ID、数据库主键、备份身份或系统偏好。
- 正确性：所有金额使用 SQLite 里的 `Long` 整数分转换，拒绝零或负数；UTF-8 BOM 支持 Windows Excel 的中文识别，逗号/引号/多行备注按 RFC 4180 转义。
- 安全性：以 `= + - @` 或空白前缀后的运算符开头的备注前加文本引用，避免 Excel 把真实用户文字当作公式。CSV 报告仅供分析，**不能用于恢复原始数据库或替代 JSON 备份**。
- 隐私：通过系统 Storage Access Framework 由用户选择保存位置，不申请广泛存储权限；CSV 文件离开本机后可能包含个人消费明细，应自行妥善保管。
- 验证：`ExpenseCsvCodecTest` 检查精确整数分、小数边界、时间排序、BOM、中文与特殊字符、公式注入、非法金额和过长备注。GitHub Actions 构建 Debug APK、运行 JUnit 单元测试与 Android lint。

**发布边界**：该功能目前先合入 `codex/qingji-v6-integration` 开发分支，不代表已发布到主分支、商店、真机或生产版 APK。构建成功也不能替代 Android 真机的系统文件保存器验收。

## 构建

使用 Android Studio 打开仓库，或在 Windows PowerShell 中运行：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

Debug APK 输出到 `app/build/outputs/apk/debug/app-debug.apk`。构建环境和验收边界见[真机验收记录](docs/acceptance-status.md)；屏幕逐项评审见[界面审查](docs/ui-audit.md)。

## 实现参考

这些公开项目用于研究原生记账产品的导航、搜索、月份筛选和统计组织方式；轻记保留自己的数据模型、品牌和 Compose 实现，没有把其预算、云端或复杂图表功能搬进来：

- [nkuppan/expensemanager](https://github.com/nkuppan/expensemanager) — 页面导航、首页到流水的层级和设置分组。
- [wansiong50-pixel/spendwise](https://github.com/wansiong50-pixel/spendwise) — 月份筛选、搜索、统计和明暗主题的交互参考。
- [Apple Human Interface Guidelines: Design principles](https://developer.apple.com/design/human-interface-guidelines/design-principles/) — 作为信息层级与可读性原则参考，不替代 Android 平台惯例。
