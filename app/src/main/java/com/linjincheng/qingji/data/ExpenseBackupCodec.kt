package com.linjincheng.qingji.data

import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import com.linjincheng.qingji.util.MoneyUtils
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.StringWriter
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

/** A validated, local-only JSON backup payload. It contains no device or preference data. */
data class ExpenseBackupPayload(
    val exportedAt: Instant,
    val appVersion: String,
    val expenses: List<ExpenseEntry>,
    val budgets: List<MonthlyBudget>,
) {
    /** Counts matching backup identities and lists every budget month that import will upsert. */
    fun preview(existingBackupIds: Set<String>): ExpenseBackupPreview {
        val existing = existingBackupIds.mapTo(HashSet()) { it.lowercase(Locale.ROOT) }
        val skipped = expenses.count { it.backupId in existing }
        return ExpenseBackupPreview(
            newExpenses = expenses.size - skipped,
            skippedExpenses = skipped,
            budgetMonths = budgets.map { YearMonth.of(it.year, it.month) }.sorted(),
        )
    }
}

data class ExpenseBackupPreview(
    val newExpenses: Int,
    val skippedExpenses: Int,
    /** The months whose local budget values will be replaced/upserted after confirmation. */
    val budgetMonths: List<YearMonth>,
)

/** Strict JSON v1 codec for the user-controlled, local backup file. */
object ExpenseBackupCodec {
    const val SCHEMA_VERSION = 1
    const val MAX_BACKUP_BYTES = 10 * 1024 * 1024
    private const val MAX_EXPENSES = 100_000
    private const val MAX_BUDGETS = 100_000
    private const val MAX_JSON_DEPTH = 16
    private const val MAX_APP_VERSION_LENGTH = 128

    private val topLevelFields = setOf("schemaVersion", "exportedAt", "appVersion", "expenses", "budgets")
    private val expenseFields = setOf(
        "backupId", "amountCents", "category", "note", "expenseDate", "createdAt", "updatedAt",
    )
    private val budgetFields = setOf("year", "month", "amountCents", "createdAt", "updatedAt")
    private val uuidPattern = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    private val integerPattern = Regex("-?(0|[1-9][0-9]*)")

    fun encode(
        expenses: List<ExpenseEntry>,
        budgets: List<MonthlyBudget>,
        appVersion: String,
        exportedAt: Instant = Instant.now(),
    ): ByteArray {
        val payload = ExpenseBackupPayload(exportedAt, appVersion, expenses, budgets)
        validatePayload(payload)

        val buffer = StringWriter()
        JsonWriter(buffer).use { json ->
            json.setIndent("  ")
            json.beginObject()
                .name("schemaVersion").value(SCHEMA_VERSION)
                .name("exportedAt").value(DateTimeFormatter.ISO_INSTANT.format(exportedAt))
                .name("appVersion").value(appVersion)
                .name("expenses").beginArray()
            expenses.forEach { expense ->
                json.beginObject()
                    .name("backupId").value(canonicalUuid(expense.backupId))
                    .name("amountCents").value(expense.amountCents)
                    .name("category").value(expense.category.code)
                    .name("note").value(expense.note)
                    .name("expenseDate").value(expense.expenseDate)
                    .name("createdAt").value(expense.createdAt)
                    .name("updatedAt").value(expense.updatedAt)
                    .endObject()
            }
            json.endArray().name("budgets").beginArray()
            budgets.forEach { budget ->
                json.beginObject()
                    .name("year").value(budget.year)
                    .name("month").value(budget.month)
                    .name("amountCents").value(budget.amountCents)
                    .name("createdAt").value(budget.createdAt)
                    .name("updatedAt").value(budget.updatedAt)
                    .endObject()
            }
            json.endArray().endObject()
        }
        val bytes = buffer.toString().toByteArray(StandardCharsets.UTF_8)
        require(bytes.size <= MAX_BACKUP_BYTES) { "备份文件超过 ${MAX_BACKUP_BYTES / 1024 / 1024} MB 限制" }
        return bytes
    }

    /** Reads at most [MAX_BACKUP_BYTES] plus one byte, without closing the caller's stream. */
    fun decode(input: InputStream): ExpenseBackupPayload {
        val output = ByteArrayOutputStream()
        val chunk = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val count = input.read(chunk)
            if (count < 0) break
            total += count
            require(total <= MAX_BACKUP_BYTES) { "备份文件超过 ${MAX_BACKUP_BYTES / 1024 / 1024} MB 限制" }
            output.write(chunk, 0, count)
        }
        return decode(output.toByteArray())
    }

    fun decode(bytes: ByteArray): ExpenseBackupPayload {
        require(bytes.size <= MAX_BACKUP_BYTES) { "备份文件超过 ${MAX_BACKUP_BYTES / 1024 / 1024} MB 限制" }
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val text = try {
            decoder.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (error: Exception) {
            throw IllegalArgumentException("备份文件不是有效的 UTF-8 JSON", error)
        }
        return decodeJson(text)
    }

    /** Convenience overload for fixtures and callers that already hold decoded text. */
    fun decode(json: String): ExpenseBackupPayload = decode(json.toByteArray(StandardCharsets.UTF_8))

    private fun decodeJson(json: String): ExpenseBackupPayload {
        val root = try {
            JsonReader(InputStreamReader(ByteArrayInputStream(json.toByteArray(StandardCharsets.UTF_8)), StandardCharsets.UTF_8)).use { reader ->
                reader.setStrictness(Strictness.STRICT)
                val value = readValue(reader, depth = 0)
                require(reader.peek() == JsonToken.END_DOCUMENT) { "JSON 文档后存在多余内容" }
                value
            }
        } catch (error: IllegalArgumentException) {
            throw error
        } catch (error: Exception) {
            throw IllegalArgumentException("备份 JSON 格式无效：${error.message ?: "无法解析"}", error)
        }

        val rootObject = root.asObject("根节点")
        rootObject.requireExactFields(topLevelFields, "根节点")
        val schemaVersion = rootObject.requiredInt("schemaVersion")
        require(schemaVersion == SCHEMA_VERSION) { "不支持的备份版本：$schemaVersion" }
        val exportedAtText = rootObject.requiredString("exportedAt")
        val exportedAt = try {
            Instant.parse(exportedAtText)
        } catch (error: Exception) {
            throw IllegalArgumentException("exportedAt 必须是有效的 ISO-8601 UTC 时间", error)
        }
        val appVersion = rootObject.requiredString("appVersion")
        val expenses = rootObject.requiredArray("expenses").mapIndexed { index, value -> parseExpense(value, index) }
        val budgets = rootObject.requiredArray("budgets").mapIndexed { index, value -> parseBudget(value, index) }
        require(expenses.size <= MAX_EXPENSES) { "备份流水数量超过限制" }
        require(budgets.size <= MAX_BUDGETS) { "备份预算数量超过限制" }
        return ExpenseBackupPayload(exportedAt, appVersion, expenses, budgets).also(::validatePayload)
    }

    private fun parseExpense(value: JsonValue, index: Int): ExpenseEntry {
        val fields = value.asObject("expenses[$index]")
        fields.requireExactFields(expenseFields, "expenses[$index]")
        val id = canonicalUuid(fields.requiredString("backupId"))
        val categoryCode = fields.requiredString("category")
        val category = ExpenseCategory.entries.firstOrNull { it.code == categoryCode }
            ?: throw IllegalArgumentException("expenses[$index].category 分类代码无效")
        val expenseDate = fields.requiredLong("expenseDate")
        validateExpenseDate(expenseDate, "expenses[$index].expenseDate")
        return ExpenseEntry(
            id = 0L,
            amountCents = fields.requiredLong("amountCents"),
            category = category,
            note = fields.requiredString("note"),
            expenseDate = expenseDate,
            createdAt = fields.requiredLong("createdAt"),
            updatedAt = fields.requiredLong("updatedAt"),
            backupId = id,
        )
    }

    private fun parseBudget(value: JsonValue, index: Int): MonthlyBudget {
        val fields = value.asObject("budgets[$index]")
        fields.requireExactFields(budgetFields, "budgets[$index]")
        return MonthlyBudget(
            year = fields.requiredInt("year"),
            month = fields.requiredInt("month"),
            amountCents = fields.requiredLong("amountCents"),
            createdAt = fields.requiredLong("createdAt"),
            updatedAt = fields.requiredLong("updatedAt"),
        ).also { budget ->
            require(budget.amountCents <= MoneyUtils.MAX_CENTS) {
                "budgets[$index].amountCents 超出允许范围"
            }
        }
    }

    private fun validatePayload(payload: ExpenseBackupPayload) {
        require(payload.appVersion.isNotBlank() && payload.appVersion.length <= MAX_APP_VERSION_LENGTH) {
            "appVersion 不能为空且不能超过 $MAX_APP_VERSION_LENGTH 字符"
        }
        require(payload.expenses.size <= MAX_EXPENSES) { "备份流水数量超过限制" }
        require(payload.budgets.size <= MAX_BUDGETS) { "备份预算数量超过限制" }
        val canonicalIds = payload.expenses.map { expense ->
            require(expense.amountCents in 1..MoneyUtils.MAX_CENTS) { "流水金额必须在 1 至 ${MoneyUtils.MAX_CENTS} 分之间" }
            require(expense.note.length <= 120) { "备注不能超过 120 字" }
            validateExpenseDate(expense.expenseDate, "expenseDate")
            require(expense.createdAt >= 0L && expense.updatedAt >= 0L) { "流水时间戳不能为负数" }
            canonicalUuid(expense.backupId)
        }
        require(canonicalIds.distinct().size == canonicalIds.size) { "备份内存在重复流水 UUID" }
        val months = payload.budgets.map { budget ->
            require(budget.amountCents in 0..MoneyUtils.MAX_CENTS) {
                "预算金额必须在 0 至 ${MoneyUtils.MAX_CENTS} 分之间"
            }
            require(budget.createdAt >= 0L && budget.updatedAt >= 0L) { "预算时间戳不能为负数" }
            YearMonth.of(budget.year, budget.month)
        }
        require(months.distinct().size == months.size) { "备份内存在重复预算月份" }
    }

    private fun validateExpenseDate(epochDay: Long, fieldName: String) {
        val date = try {
            LocalDate.ofEpochDay(epochDay)
        } catch (error: Exception) {
            throw IllegalArgumentException("$fieldName 不是有效日期", error)
        }
        require(date.year in 1..9999) { "$fieldName 年份必须在 1..9999" }
    }

    private fun canonicalUuid(value: String): String {
        require(uuidPattern.matches(value)) { "流水备份身份必须为标准 UUID" }
        val canonical = try {
            UUID.fromString(value).toString()
        } catch (error: IllegalArgumentException) {
            throw IllegalArgumentException("流水备份身份必须为有效 UUID", error)
        }
        require(canonical.equals(value, ignoreCase = true)) { "流水备份身份必须为标准 UUID" }
        return canonical
    }

    private fun readValue(reader: JsonReader, depth: Int): JsonValue {
        require(depth <= MAX_JSON_DEPTH) { "JSON 嵌套层级过深" }
        return when (reader.peek()) {
            JsonToken.BEGIN_OBJECT -> {
                reader.beginObject()
                val fields = LinkedHashMap<String, JsonValue>()
                while (reader.hasNext()) {
                    val name = reader.nextName()
                    require(!fields.containsKey(name)) { "JSON 对象中存在重复字段：$name" }
                    fields[name] = readValue(reader, depth + 1)
                }
                reader.endObject()
                JsonValue.ObjectValue(fields)
            }
            JsonToken.BEGIN_ARRAY -> {
                reader.beginArray()
                val values = ArrayList<JsonValue>()
                while (reader.hasNext()) {
                    require(values.size < MAX_EXPENSES.coerceAtLeast(MAX_BUDGETS)) { "JSON 数组元素超过限制" }
                    values += readValue(reader, depth + 1)
                }
                reader.endArray()
                JsonValue.ArrayValue(values)
            }
            JsonToken.STRING -> JsonValue.StringValue(reader.nextString())
            JsonToken.NUMBER -> JsonValue.NumberValue(reader.nextString())
            JsonToken.BOOLEAN -> JsonValue.BooleanValue(reader.nextBoolean())
            JsonToken.NULL -> {
                reader.nextNull()
                JsonValue.NullValue
            }
            else -> throw IllegalArgumentException("JSON 在 ${reader.path} 处缺少值")
        }
    }

    private sealed interface JsonValue {
        data class ObjectValue(val fields: Map<String, JsonValue>) : JsonValue
        data class ArrayValue(val values: List<JsonValue>) : JsonValue
        data class StringValue(val value: String) : JsonValue
        data class NumberValue(val raw: String) : JsonValue
        data class BooleanValue(val value: Boolean) : JsonValue
        data object NullValue : JsonValue
    }

    private fun JsonValue.asObject(label: String): Map<String, JsonValue> =
        (this as? JsonValue.ObjectValue)?.fields ?: throw IllegalArgumentException("$label 必须是对象")

    private fun Map<String, JsonValue>.requireExactFields(expected: Set<String>, label: String) {
        val missing = expected - keys
        val unknown = keys - expected
        require(missing.isEmpty()) { "$label 缺少必需字段：${missing.sorted().joinToString()}" }
        require(unknown.isEmpty()) { "$label 含有不支持的字段：${unknown.sorted().joinToString()}" }
    }

    private fun Map<String, JsonValue>.requiredString(name: String): String =
        (getValue(name) as? JsonValue.StringValue)?.value
            ?: throw IllegalArgumentException("$name 必须是字符串")

    private fun Map<String, JsonValue>.requiredArray(name: String): List<JsonValue> =
        (getValue(name) as? JsonValue.ArrayValue)?.values
            ?: throw IllegalArgumentException("$name 必须是数组")

    private fun Map<String, JsonValue>.requiredLong(name: String): Long {
        val raw = (getValue(name) as? JsonValue.NumberValue)?.raw
            ?: throw IllegalArgumentException("$name 必须是整数")
        require(integerPattern.matches(raw)) { "$name 必须是整数" }
        return raw.toLongOrNull() ?: throw IllegalArgumentException("$name 超出整数范围")
    }

    private fun Map<String, JsonValue>.requiredInt(name: String): Int {
        val value = requiredLong(name)
        require(value in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) { "$name 超出整数范围" }
        return value.toInt()
    }
}
