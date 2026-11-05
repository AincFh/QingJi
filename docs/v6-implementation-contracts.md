# QingJi v6 并行实现契约

本文冻结跨任务的最小接口和所有权。所有实现以当前 `codex/qingji-v6-integration` 分支为起点；任务分支通过独立 worktree 开发，由主集成 Agent 逐项合入。

## 产品导航与设计

- 保留 `HOME / RECORDS / INSIGHTS / SETTINGS` 四个主目的地和中央全局新增操作。
- `ExpenseViewModel.Screen` 与 `MainActivity` 是二级路由/底部导航的唯一所有者。各页面只收 state 与 callback；工作流任务不得自行修改根导航。
- Calendar 是可从账单和统计进入的二级页；Budget 从首页摘要、统计和设置到达；导入需独立预览/确认。
- 品牌颜色：暖纸白 `#F8F4E9`、深松绿 `#245B43`、柿橙 `#DE601C`。可扩展深浅主题语义色，不替换品牌。可交互目标至少 48dp，所有新图表/进度在 TalkBack 语义中有文字和值。

## 共享消费筛选

```kotlin
data class EpochDayRange(val startInclusive: Long, val endExclusive: Long)

data class ExpenseFilter(
    val dateRange: EpochDayRange? = null,
    val category: ExpenseCategory? = null,
    val keyword: String = "",
)
```

- 日期范围一律半开 `[startInclusive, endExclusive)`，单日是 `[day, day + 1)`，月份是当月首日至次月首日。
- 日期、类别、关键词按 AND 相交；空筛选代表全部流水。
- `ExpenseViewModel` 持有过滤条件和返回上下文。趋势、分类、日历、首页的跳转必须调用同一套入口；详情/编辑/删除后重查同一个过滤结果。
- `DailyExpenseTotal` 增加 `transactionCount: Int`；零消费日必须有金额 0、笔数 0 的真实数据点。
- `HomeUiState` 持有 `selectedTrendEpochDay: Long?` 与 `selectedCategory: ExpenseCategory?`；Home/Insights 的可视化选中态由这两个值驱动，并在切换 Tab、进入流水、返回时保留。

## 金额隐藏的产品语义

- `hideAmounts` 隐藏精确货币数值及 TalkBack/tooltip 中的金额文本。
- 趋势形状、活动密度、分类占比和笔数继续展示为相对消费洞察；设置页应明确说明，避免把金额遮罩误解为完整隐私屏幕。

## SQLite v2、预算与备份

- 主数据仍在 SQLite。`ExpenseEntry.amountCents` 为整数分，日期为本地 `LocalDate.toEpochDay()`。
- v1→v2 仅允许 ADD COLUMN、建新表和索引，不删表、不重建 `expenses`。迁移必须保留旧记录 id、金额、分类、备注、日期和时间戳。备份身份 `backup_id` 为稳定 UUID，新记录和导入记录都写入；编辑不改 UUID。
- `monthly_budgets` 主键为 `(year, month)`，金额为非负整数分。无行=未设置；存在且金额为 0=用户明确设为零预算。实际已用金额始终由当月流水 SUM 计算，不缓存。
- Calendar 查询返回日期、总分、笔数；由界面层补齐空日期。
- JSON 备份包含 `schemaVersion`、`exportedAt`、`appVersion`、消费记录和每月预算；不导出设备信息或主题/隐私偏好。只通过 SAF 文件选择器，不申请宽泛存储权限。
- 导入策略固定为“合并流水”：相同 `backup_id` 跳过，新 UUID 插入；备份中的预算月份在确认后覆盖本地同月预算。预览明确展示将新增/跳过的流水数和将替换的预算月份；所有解析/校验先于写入，写事务失败必须完整回滚。
- 仅接受支持的 schema 版本；金额、日期、分类、必填字段、UUID 和预算范围均校验。导入/恢复不得先清空本地数据。

## 模块所有权

- 主集成 Agent：`MainActivity.kt`、`ExpenseViewModel.kt`、统一过滤状态/路由、工作树合并和最终真机验收。
- 数据子任务：`ExpenseDatabaseHelper.kt`、`ExpenseEntry.kt`、`ExpenseRepository.kt`、预算/聚合数据模型和隔离数据库验证。不得改 `MainActivity.kt`、`ExpenseViewModel.kt` 或各 UI 页面；若需数据库 API 改动，先按本契约提供并在独立分支实现。
- 图表子任务：`HomeScreen.kt`、`AnalyticsScreen.kt` 与专属 chart components；不得改共享 viewmodel、Repository、MainActivity 或 Settings。
- 快速记账子任务：`ExpenseDetailScreen.kt`、`ExpenseFormScreen.kt` 和专属表单 UI；不得改共享 viewmodel、Repository、MainActivity 或数据库。仅以新增 callbacks 暴露操作。
- Calendar、Backup、Settings 子任务后续分波，文件边界另在任务消息中冻结。
- UI 子任务：修改各自明确划分的屏幕/组件；不得改数据库、Repository、MainActivity 或 ViewModel 公共接口。需要新接口时先在任务说明中提出，由主集成 Agent回传冻结契约。
- 文档和测试不能把旧 v5 报告里的数字当作 v6 结果。APK/截图/报告只来自最终通过验收的源码。

## 验收门槛

- 趋势点和类别都能带着完整筛选打开账单；选中日期有金额/笔数；零值日期可操作；返回后上下文保留。
- 预算未设置、0、正常使用和超支状态都正确；增改删消费后同月余额立即更新。
- 月历能切换月份，点击日期显示真实金额/笔数并打开流水。
- 复制流水是新记录：默认日期为今天，金额/分类/备注可确认编辑，源记录不变。
- SAF 导出/导入真实往返；重复导入幂等；坏文件、未知版本和中途失败不改动现有流水。
- v1 测试 fixture 证明升级前后旧字段逐行不变；真机 QA 只用标记数据或隔离测试库，不清理个人账本。
- 构建、JVM、数据库/备份测试与单台真机结果分别记录；当前一台设备的证据不表述为多 OS 覆盖。
