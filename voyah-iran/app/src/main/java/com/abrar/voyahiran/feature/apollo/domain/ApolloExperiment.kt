package com.abrar.voyahiran.feature.apollo.domain

/** Values traced to the OEM DriveAssistanceFragment and its generated binding. */
enum class ApolloExperiment(val field: String, val title: String, val choices: Map<Int, String>, val on: Int? = null) {
    NAVIGATION("ANP_SWITCH", "هدایت ناوبری / ANP", linkedMapOf(1 to "خاموش", 2 to "روشن"), 2),
    SPEED("ISA_ISLC_SWITCH", "محدودیت سرعت هوشمند", linkedMapOf(2 to "خاموش", 1 to "روشن"), 1),
    SPEED_MODE("ISA_ISLC_MODE", "حالت محدودیت سرعت هوشمند", linkedMapOf(2 to "خودکار", 3 to "دستی", 4 to "فقط نمایش محدودیت")),
    SPEED_WARNING("ISA_ISLC_OVER_SPEED_WARNING_SWITCH", "هشدار عبور از محدودیت سرعت", linkedMapOf(1 to "خاموش", 2 to "روشن"), 2),
    SPEED_OFFSET("ISA_ISLC_SPEED_OFFSET_STYLE", "روش اختلاف با محدودیت سرعت", linkedMapOf(1 to "مقدار ثابت", 2 to "درصدی")),
    LANE_CONFIRM("ALC_SWITCH", "تأیید تغییر لاین خودکار", linkedMapOf(2 to "با تأیید راننده", 1 to "بدون تأیید")),
    LANE_SCENARIOS("ALC_SETTING", "موقعیت‌های تغییر لاین خودکار", linkedMapOf(
        1 to "لاین سریع", 2 to "سبقت", 3 to "فاصله از خودروی بزرگ",
        4 to "لاین سریع + سبقت", 5 to "لاین سریع + خودروی بزرگ",
        6 to "سبقت + خودروی بزرگ", 7 to "هر سه حالت"));

    fun accepts(value: Int) = value in choices
    val off: Int? get() = on?.let { enabled -> choices.keys.single { it != enabled } }
}
