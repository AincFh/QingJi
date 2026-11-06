# QingJi v6 产品差距矩阵

初始差距审计日期：2026-11-05，源码基线 `27a8bb1`。下面第一张表记录初始差距，不代表当前集成状态。当前代码状态另见“首波集成快照”；v5 验收记录只作历史基线，不代表 v6 已通过。

## 差距

| 状态 | 现有能力 | v6 判断与动作 |
|---|---|---|
| 已有，可复用 | 四个主 Tab 加中央记账；新增、编辑、详情、删除确认；SQLite v1；金额分；主题三态、金额隐藏、默认分类；7/30 日趋势与选日；分类金额/占比；跨月文本搜索和分类/月筛选 | 保留现有路径与本地数据，不重写项目，不重复造同一套能力。 |
| 已有但不完整 | 趋势可以选日期，但没有笔数和“查看流水”联动；统计页类别行不可钻取；筛选分散在搜索、分类、月份字段；首页/统计/账单之间不能统一恢复条件 | 引入共享日期范围、分类和关键词筛选；图表详情同时展示金额/笔数并有明确流水入口；返回后保留月份、筛选及来源选择。 |
| 尚未实现 | 月预算和真实剩余额；从 SQLite 聚合的整月日历；复制历史流水；用户主动 SAF JSON 备份与恢复；SQLite v1→v2 非破坏迁移与导入事务测试 | 本轮实现并提供对应的数据层、界面、测试及真机验证。 |
| 本轮无需实现 | 收入、多账本、云/银行同步、AI、周期记账、自定义类别、复杂财务图表 | 暂不扩展到未形成可靠闭环的能力。 |

## 用户价值与设计方向

- 目标用户是希望快速记录日常消费、看懂钱花在哪里、查回具体记录并规划本月支出的个人用户。
- 保留四 Tab 和中央“记一笔”；日历作为账单/统计可到达的二级功能，预算通过首页摘要、统计和设置入口进入。
- 品牌基线保持暖纸白 `#F8F4E9`、深松绿 `#245B43`、柿橙 `#DE601C`。学习 Apple HIG 的信息优先级、对齐与克制；遵循 Android 原生返回、可访问性与 Material 触控习惯，不复制 GitHub 示例项目的品牌视觉。
- 优先选择“金额焦点—消费趋势—分类与流水”的清晰层级；重复图表合并，避免每条统计都变成同权重卡片。
- 所有图表、预算、日历说明复用金额隐藏状态；图表同时提供文字、数量和语义标签，不单靠颜色表达。

## 首波集成快照

审查对象：`codex/qingji-v6-integration`，提交 `d49b52f`。这些状态是源码/JVM 构建证据，不等于整机产品验收。

| 范围 | 当前状态 |
|---|---|
| 共享日期/类别/关键词筛选 | 已实现为半开日期范围，三项 AND 组合；JVM 边界测试通过。 |
| 7/30 日趋势与分类分析 | 已有金额/笔数、零值日和可选图表组件；`MainActivity` 已把趋势选日/分类点选接到统一账单筛选并保留返回状态。 |
| SQLite v2 与月预算数据层 | 已有 v1→v2 非破坏迁移、预算、按日金额/笔数聚合、幂等流水合并 API；instrumentation 源码可编译，设备实际执行仍为 0 项（设备策略拦截）。 |
| 历史复制 UI | 详情按钮和表单复制态已实现；ViewModel 加载/保存/返回路径已接线（`copyMode`、`openCopy`）。 |
| Budget/Calendar/SAF/Settings 入口 | 预算、日历、备份页面、设置入口、SAF 导出/导入、根导航与系统返回均已接线到 `MainActivity`。 |
| 真机与隐私审计 | 已连接真机验证金额隐私（读屏统一「金额已隐藏」、无泄露）、深浅主题、删除流程与持久化；`connectedDebugAndroidTest` 仍被设备策略拦截（0 项执行）。 |
| 收尾合并 | `codex/qingji-v6-consumer-polish`（`8e13d42`）已 cherry-pick 为 `62d075c`；统计页总额读屏补充为 `0d60b03`；`mipmap-anydpi-v26` 的 lint 提示经验证为自适应图标误报，予以保留。 |

金额隐藏的产品语义：隐藏的是精确货币数值，包括金额文字、提示和 TalkBack 数值；日期/笔数、趋势形状与分类占比仍用于保留消费洞察。设置页应明确说明这一点，避免用户误解为整页内容隐藏。

## 市场与开源参考

2026-11-05 查阅的 Google Play 官方产品页把快速记账、类别/趋势分析、预算和备份迁移作为消费记账产品的常见功能示例。它们是能力对照，不是市场份额证明：

- [Money Manager（Realbyte）](https://play.google.com/store/apps/details?id=com.realbyteapps.moneymanagerfree)
- [Monefy](https://play.google.com/store/apps/details?id=com.monefy.app.lite)
- [Wallet](https://play.google.com/store/apps/details?id=com.droid4you.application.wallet)

[SpendWise](https://github.com/wansiong50-pixel/spendwise) 的 MIT 开源仓库展示快速录入、日历、日期筛选、预算和 JSON 备份等产品模式；只借鉴问题拆解，不复制其代码或视觉风格。布局、隐私和访问原则参考 [Apple HIG Layout](https://developer.apple.com/design/human-interface-guidelines/layout)、[Apple Design Principles](https://developer.apple.com/design/human-interface-guidelines/design-principles)、[Android Accessibility](https://developer.android.com/guide/topics/ui/accessibility/apps)。
