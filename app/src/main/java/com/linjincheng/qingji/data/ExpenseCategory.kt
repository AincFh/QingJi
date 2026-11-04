package com.linjincheng.qingji.data

/**
 * 固定五个消费分类（契约 §2 冻结），不允许用户自定义。
 */
enum class ExpenseCategory(val code: String, val label: String) {
    FOOD("FOOD", "餐饮"),
    TRANSPORT("TRANSPORT", "交通"),
    SHOPPING("SHOPPING", "购物"),
    DAILY("DAILY", "生活"),
    OTHER("OTHER", "其他");

    companion object {
        /**
         * 由稳定代码还原分类。未知代码（理论上只可能来自外部篡改的数据库）
         * 归入 OTHER，保证统计总额不丢、界面不崩溃。
         */
        fun fromCode(code: String): ExpenseCategory =
            entries.firstOrNull { it.code == code } ?: OTHER
    }
}
