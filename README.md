# 轻记 QingJi

轻记是一款面向个人日常消费的原生 Android 记账原型。核心流程是快速记下一笔、回看历史流水，并从月度汇总、类别占比和近期趋势中了解消费去向。

## 产品与设计

- 原生 Kotlin、Jetpack Compose、Material 3 与 SQLite；数据默认只保存在本机。
- 四个底部目的地：首页、账单、统计、设置；“记一笔”作为全局主操作放在底部中央。
- 视觉使用暖纸白、深松绿与柿橙，深色模式切换到石墨色阶；强调清晰层级、内容优先和克制的容器。
- 保留 Android 导航、日期选择、输入法和系统主题行为，同时参考 Apple HIG 的清晰层级与可读性原则。

## 功能

- 记录、编辑、查看和确认删除日常支出；金额以整数分保存。
- 设置中可**手动导出全部消费 CSV**：先确认敏感信息，再通过系统文件选择器选取保存位置；只读取本机 SQLite，不上传网络，金额精确到分，支持中文与 Excel UTF-8。
- 首页按月份展示支出总额、笔数、七日趋势、分类结构和近期流水。
- 账单支持全历史搜索、分类筛选和月份筛选，组合条件共同作用。
- 统计展示分类金额与占比、七日或三十日趋势，以及与上月同期的比较。
- 设置支持跟随系统/浅色/深色、金额隐私显示和新记录默认分类。
- **月度预算**：可在设置中指定每月重复的本地消费目标，首页根据当前选择月份的 SQLite 实际总支出展示预算余额、80% 接近提醒、刚好用完和超支状态；关闭预算不会删除记录。
- 新增和编辑共用表单；键盘、无效金额、未保存返回和删除操作都有明确反馈。

当前产品聚焦单人、本机、支出记录。收入、云同步、账本管理和 AI 对话不在本轮范围内；月度预算是单一本地循环额度，不包含多账本或自动扣款。**CSV 导出属于单向数据副本，不支持重新导入覆盖现有账本；保存前应确认文件接收方可信。**

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
