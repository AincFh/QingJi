// 轻记（QingJi）根构建脚本。
// 版本矩阵锁定见 docs/契约_轻记工程契约.md §0：AGP 8.7.3 / Kotlin 2.0.21（含 compose 插件），不得擅自升级。
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}
