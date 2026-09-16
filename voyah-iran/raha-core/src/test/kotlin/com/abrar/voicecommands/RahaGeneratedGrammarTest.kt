package com.abrar.voicecommands

import com.abrar.raha.RahaCommandRecognizer
import org.junit.Assert.*
import org.junit.Test

class RahaGeneratedGrammarTest {
    private val parser = RahaCommandRecognizer()
    private fun same(seed: String, variant: String) {
        val expected = parser.recognize(seed)
        assertTrue("Invalid seed: $seed", expected is Recognition.Matched)
        assertEquals("$seed => $variant", expected, parser.recognize(variant))
    }
    @Test fun generatedOperatorTargetOrdersAcrossAllSeventeenActions() {
        val seeds = listOf(
            "تهویه رو روشن کن", "ای سی رو روشن کن", "مکس کولر رو روشن کن",
            "تهویه رو اتومات کن", "دما رو دو درجه زیاد کن", "فن رو دو پله کم کن",
            "باد رو به صورت بده", "گردش هوا رو خودکار کن", "بخارگیر جلو رو روشن کن",
            "بخارگیر عقب رو خاموش کن", "تهویه رو هماهنگ کن", "دما رو روی ۲۳ بذار",
            "فن رو روی سه بذار", "گرمکن صندلی رو روشن کن", "سردکن صندلی رو روشن کن",
            "ماساژ صندلی رو روشن کن", "ماساژ صندلی رو موجی کن"
        )
        for (seed in seeds) {
            val (target, operator) = seed.split(" رو ")
            // Exercise the requested keyword semantics across all 17 phase-one actions.
            val negated = seed.replace(" کن", " نکن").replace(" بذار", " نذار")
            same(seed, negated)
            for (prefix in listOf("", "لطفا ", "رها ", "رها لطفا ", "خواهشا ")) {
                for (marker in listOf("رو", "را", "")) {
                    same(seed, "$prefix$target $marker $operator")
                }
                same(seed, "$prefix$operator $target")
            }
        }
    }
    @Test fun generatedSeatAliasesSidesAndColloquialObjectMarkers() {
        for (feature in listOf("گرمکن", "گرم کن", "سردکن", "سرد کن", "خنک کن", "ماساژ", "ماساژور")) {
            for (side in listOf("", "راننده", "چپ", "سمت چپ", "شاگرد", "راست", "سمت راست")) {
                for (operator in listOf("روشن کن", "خاموش کن", "فعال کن", "غیر فعال کن", "راه بنداز")) {
                    val seed = "$feature صندلی $side رو $operator"
                    for (prefix in listOf("", "لطفا ", "رها خواهشا ")) {
                        same(seed, "$prefix$feature صندلی $side و $operator")
                        same(seed, "$prefix$operator $feature صندلی $side")
                    }
                }
            }
        }
    }
    @Test fun meaningfulSlotsDoNotRequireObjectMarkersOrAuxiliaryVerbs() {
        val expected = parser.recognize("سردکن صندلی راننده رو روشن کن")
        assertTrue(expected is Recognition.Matched)
        for (text in listOf("سردکن روشن", "سردکن را روشن کن", "روشن کردن سردکن", "سردکن ر روشن کن"))
            assertEquals(text, expected, parser.recognize(text))
        for (target in listOf("سردکن", "گرمکن", "ماساژ", "کولر", "بخاری", "تهویه", "ای سی")) {
            for (operator in listOf("روشن", "خاموش", "فعال", "غیرفعال")) {
                val seed = "$target رو $operator کن"
                for (marker in listOf("", "رو", "را", "ر")) {
                    same(seed, "$target $marker $operator")
                    same(seed, "$target $marker $operator کن")
                }
                same(seed, "$operator کردن $target")
                same(seed, "$operator $target")
            }
        }
        // Ignoring grammar particles must not supply a missing operator or target.
        for (text in listOf("سردکن", "روشن", "سردکن ر", "سردکن ر روشن و خاموش"))
            assertTrue(text, parser.recognize(text) is Recognition.Unknown)
    }
    @Test fun negativeAuxiliaryDoesNotOverrideExplicitKeyword() {
        for (aux in listOf("نکن", "نکنید", "نکنین", "نکنی", "نکنه", "نکنن", "نشه", "نشود", "نباشه", "نباشد")) {
            same("سردکن رو روشن کن", "سردکن رو روشن $aux")
            same("سردکن رو خاموش کن", "سردکن رو خاموش $aux")
        }
        same("سردکن رو روشن کن", "سردکن رو روشن نکن")
        same("سردکن رو روشن کن", "سردکن صندلی و روشن نکن")
    }
    @Test fun newSeatAndCabinRequestsHaveExactParameters() {
        fun expect(text: String, action: String, vararg values: Pair<String, Any>) {
            val result = parser.recognize(text)
            assertTrue("$text => $result", result is Recognition.Matched)
            val command = (result as Recognition.Matched).command
            assertEquals(text, action, command.action)
            assertEquals(text, values.toMap(), command.parameters)
        }
        expect("ماساژ سطح ۳", "SET_SEAT_MASSAGE", "seat" to "driver", "enabled" to true, "level" to 3)
        expect("ماساژ موجی", "SET_SEAT_MASSAGE_PATTERN", "seat" to "driver", "pattern" to "wave")
        expect("ماساژ چرخشی", "SET_SEAT_MASSAGE_PATTERN", "seat" to "driver", "pattern" to "rolling")
        expect("سردکن صندلی رو زیاد کن", "ADJUST_SEAT_VENTILATION", "seat" to "driver", "delta" to 1)
        expect("سردکن صندلی رو کم کن", "ADJUST_SEAT_VENTILATION", "seat" to "driver", "delta" to -1)
        expect("دمای اتاق رو زیاد کن", "ADJUST_CABIN_TEMPERATURE", "zone" to "front", "delta" to 1.0)
        for ((side, seat) in listOf("" to "driver", "راننده" to "driver", "چپ" to "driver", "راست" to "passenger", "شاگرد" to "passenger")) {
            for (alias in listOf("سردکن", "سرد کن", "خنک کن", "تهویه")) {
                for ((word, delta) in listOf("زیاد" to 1, "کم" to -1, "بیشتر" to 1, "کمتر" to -1)) {
                    expect("$alias صندلی $side $word", "ADJUST_SEAT_VENTILATION", "seat" to seat, "delta" to delta)
                    expect("لطفا $alias صندلی $side رو دو سطح $word کن", "ADJUST_SEAT_VENTILATION", "seat" to seat, "delta" to delta * 2)
                }
            }
            expect("ماساژ $side سطح ۲", "SET_SEAT_MASSAGE", "seat" to seat, "enabled" to true, "level" to 2)
            expect("ماساژ $side چرخشی", "SET_SEAT_MASSAGE_PATTERN", "seat" to seat, "pattern" to "rolling")
        }
        for ((side, zone) in listOf("" to "front", "راننده" to "driver", "چپ" to "driver", "شاگرد" to "passenger", "دو طرف" to "front")) {
            expect("کولر $side روشن", "SET_CLIMATE_POWER", "zone" to zone, "enabled" to true, "acEnabled" to true, "temperatureMaxC" to 23.0)
            expect("دمای اتاق $side ۲۳", "SET_CABIN_TEMPERATURE", "zone" to zone, "degreesC" to 23.0)
            expect("دما $side زیاد", "ADJUST_CABIN_TEMPERATURE", "zone" to zone, "delta" to 1.0)
            expect("فن $side کم", "ADJUST_FAN_SPEED", "zone" to zone, "delta" to -1)
        }
    }
    @Test fun carVocabularyIsReusableAndContainsDomainWords() {
        val grammar = com.abrar.raha.RahaSpeechVocabulary.phrases()
        assertEquals(grammar.size, grammar.distinct().size)
        for (word in listOf("[unk]", "سرد", "کن", "صندلی", "سطح", "چرخشی", "اتاق", "شاگرد", "راننده", "نیم"))
            assertTrue(word, word in grammar)
        assertFalse(grammar.any { "صبر" in it.split(' ') })
        assertTrue(grammar.size < 10000)
    }
    @Test fun semanticFragmentsNeedNoAuxiliaryVerb() {
        val groups = listOf(
            "کولر مکس روشن کن" to listOf("کولر رو تا ته زیاد کن", "حداکثر سرمایش", "خیلی گرمه", "هوا خیلی گرمه", "سرمایش حداکثری"),
            "کولر روشن کن" to listOf("گرمه", "گرممه", "هوا گرمه"),
            "تهویه خودکار کن" to listOf("تهویه خودکار", "تهویه اتومات", "کولر خودکار"),
            "سرعت فن رو زیاد کن" to listOf("سرعت فن و زیاد", "سرعت فن زیاد", "فن زیاد", "زیاد کردن سرعت فن"),
            "فن روی چهار بذار" to listOf("فن سطح ۴", "سرعت فن ۴", "فن چهار", "سرعت فن و چهار"),
            "باد به صورت بده" to listOf("باد به سمت صورت", "جهت باد به سمت صورت", "جهت باد صورت"),
            "گردش هوا خارجی کن" to listOf("هوای تازه می خوام", "هوای تازه", "هوای بیرون میخوام"),
            "گردش داخلی روشن کن" to listOf("تهویه هوای داخل رو روشن کن", "تهویه هوای داخل", "گردش داخلی"),
            "بخارگیر جلو فعال کن" to listOf("بخارزدایی جلو رو فعال کن", "بخار زدایی جلو فعال"),
            "تهویه جلو یکسان کن" to listOf("تهویه جلو رو یکسان کرد", "دمای جلو رو یکسان کرد", "دمای جلو یکسان", "تهویه جلو یکسان")
        )
        for ((seed, variants) in groups) for (variant in variants) {
            same(seed, variant)
            for (prefix in listOf("رها ", "لطفا ", "رها خواهشا "))
                same(seed, prefix + variant)
        }
        val hot = parser.recognize("گرمه") as Recognition.Matched
        val veryHot = parser.recognize("خیلی گرمه") as Recognition.Matched
        assertEquals("SET_CLIMATE_POWER", hot.command.action)
        assertEquals(mapOf("enabled" to true, "zone" to "front", "acEnabled" to true, "temperatureMaxC" to 23.0), hot.command.parameters)
        assertEquals("SET_MAX_COOLING", veryHot.command.action)
        assertEquals(mapOf("enabled" to true), veryHot.command.parameters)
    }
    @Test fun levelBoundsAreAbsoluteAndKeepTargets() {
        fun expected(text: String, action: String, targetKey: String, target: String, level: String) {
            val result = parser.recognize(text)
            assertTrue("$text => $result", result is Recognition.Matched)
            val command = (result as Recognition.Matched).command
            assertEquals(text, action, command.action)
            val params = mutableMapOf<String, Any>(targetKey to target, "level" to level)
            if (targetKey == "seat") params["enabled"] = true
            assertEquals(text, params, command.parameters)
        }
        expected("گرمکن صندلی رو روی حداکثر بذار", "SET_SEAT_HEATING", "seat", "driver", "max")
        expected("فن کولر رو تا ته زیاد کن", "SET_FAN_SPEED", "zone", "front", "max")
        expected("سردکن رو روی کمترین مقدار تنظیم کن", "SET_SEAT_VENTILATION", "seat", "driver", "min")
        for ((noun, action) in listOf("گرمکن صندلی" to "SET_SEAT_HEATING", "سردکن" to "SET_SEAT_VENTILATION", "ماساژ" to "SET_SEAT_MASSAGE", "فن کولر" to "SET_FAN_SPEED")) {
            for ((side, target) in listOf("" to if(action=="SET_FAN_SPEED") "front" else "driver", "چپ" to "driver", "راست" to "passenger")) {
                for ((bound, aliases) in listOf(
                    "max" to listOf("حداکثر", "بیشترین مقدار", "بالاترین سطح", "آخرین درجه", "ماکزیمم", "تا ته", "تا آخر", "نهایت قدرت"),
                    "min" to listOf("حداقل", "کمترین مقدار", "پایین ترین سطح", "مینیمم", "کم ترین شدت", "حد اقل")
                )) for (alias in aliases) {
                    for (phrase in listOf("$noun $side روی $alias بذار", "$noun $side $alias", "لطفا $noun $side رو روی $alias تنظیم کن"))
                        expected(phrase, action, if(action=="SET_FAN_SPEED") "zone" else "seat", target, bound)
                }
            }
        }
        expected("فن کولر رو تا ته کم کن", "SET_FAN_SPEED", "zone", "front", "min")
        expected("سردکن رو تا آخر کم کن", "SET_SEAT_VENTILATION", "seat", "driver", "min")
        same("فن کولر حداکثر", "فن کولر رو تا ته زیاد کن")
        same("گرمکن حداکثر", "گرمکن رو تا آخر زیاد کن")
    }
    @Test fun activeMeansEnableAcrossFeatures() {
        same("کولر رو فعال کن", "کولر رو اکتیو کن")
        same("ماساژور رو فعال کن", "ماساژور رو اکتیو کن")
        same("سردکن فعال", "سردکن اکتیو")
        for (target in listOf("کولر", "ماساژور", "سردکن", "گرمکن", "تهویه", "ای سی", "بخارگیر جلو", "سینک")) {
            for (variant in listOf("$target اکتیو", "$target رو اکتیو کن", "لطفا اکتیو کن $target"))
                same("$target فعال", variant)
        }
        assertTrue("اکتیو" in com.abrar.raha.RahaSpeechVocabulary.phrases())
    }
    @Test fun veryColdRequestsMaximumTemperatureButColdKeepsFloor() {
        for ((side, zone) in listOf("" to "front", "راننده" to "driver", "شاگرد" to "passenger")) {
            for (phrase in listOf("سرده", "سردمه", "سردم شده")) {
                for (very in listOf(false, true)) {
                    val text = "$side ${if(very) "خیلی " else ""}$phrase"
                    val result = parser.recognize(text)
                    assertTrue("$text => $result", result is Recognition.Matched)
                    val command = (result as Recognition.Matched).command
                    assertEquals("SET_CLIMATE_POWER", command.action)
                    val expected = mapOf("enabled" to true, "acEnabled" to true, "zone" to zone) +
                        if(very) mapOf("temperatureLevel" to "max") else mapOf("temperatureMinC" to 26.0)
                    assertEquals(text, expected, command.parameters)
                    if(very) assertTrue(command.description.contains("دما: حداکثر"))
                }
            }
        }
        same("خیلی سرده", "اینجا خیلی سرده")
        same("خیلی سرده", "رها هوا خیلی سرده")
        val cold = (parser.recognize("خیلی سرده") as Recognition.Matched).command
        assertFalse(cold.parameters.containsKey("temperatureMaxC"))
        assertFalse(cold.parameters.containsKey("temperatureMinC"))
    }
    @Test fun borrowedWordsKeepTheirDomainMeaning() {
        for (word in listOf("ماکزیمم", "ماکسیمم", "ماکزیموم", "ماکسیموم", "مکس", "ماکس", "فول")) {
            same("فن حداکثر", "فن $word")
            same("گرمکن حداکثر", "گرمکن رو روی $word بذار")
            same("سردکن شاگرد حداکثر", "سردکن شاگرد $word")
        }
        for (word in listOf("مینیمم", "مینیموم", "مین")) {
            same("فن حداقل", "فن $word")
            same("ماساژ حداقل", "ماساژ $word")
        }
        for (word in listOf("اتو", "اوتو", "اتومات", "اتوماتیک"))
            same("تهویه خودکار", "تهویه $word")
        same("بخاری روشن", "هیتر روشن")
        same("دمای بخاری ۲۶", "دمای هیتر ۲۶")
        same("فن سطح ۴", "فن لول ۴")
        same("ماساژ سطح ۳", "ماساژ لیول ۳")
        same("سرعت فن زیاد", "اسپید فن زیاد")
        same("بخارگیر جلو روشن", "دیفراست جلو روشن")
        same("تهویه جلو یکسان", "تهویه جلو سینک")
        val words=com.abrar.raha.RahaSpeechVocabulary.phrases().flatMap { it.split(' ') }.toSet()
        for(word in listOf("ماکسیموم", "ماکزیموم", "مینیموم", "مکس", "مین", "فول", "اتو", "اوتو", "سینک", "هیتر", "دیفراست", "لول", "اسپید"))
            assertTrue(word, word in words)
    }
    @Test fun coolingStrengthInvertsTemperatureButExplicitTemperatureDoesNot() {
        fun delta(text: String, expected: Double, zone: String = "front") {
            val result = parser.recognize(text)
            assertTrue("$text => $result", result is Recognition.Matched)
            val command = (result as Recognition.Matched).command
            assertEquals(text, "ADJUST_CABIN_TEMPERATURE", command.action)
            assertEquals(text, mapOf("zone" to zone, "delta" to expected), command.parameters)
        }
        for (word in listOf("زیاد", "بیشتر", "بالا")) delta("کولر رو $word کن", -1.0)
        for (word in listOf("کم", "کمتر", "پایین")) delta("کولر رو $word کن", 1.0)
        delta("کولر شاگرد رو دو درجه زیاد کن", -2.0, "passenger")
        delta("کولر چپ رو دو درجه کم کن", 2.0, "driver")
        for (noun in listOf("دما", "دمای کولر", "درجه کولر", "بخاری")) {
            delta("$noun رو زیاد کن", 1.0)
            delta("$noun رو کم کن", -1.0)
        }
        delta("کولر رو خنک تر کن", -1.0)
        delta("کولر رو گرم تر کن", 1.0)
        same("فن حداکثر", "فن کولر رو تا ته زیاد کن")
        same("کولر مکس", "کولر رو تا ته زیاد کن")
        same("فن دو پله زیاد", "فن کولر دو پله زیاد")
    }
    @Test fun temperatureBoundsAreNotCoolingPowerOrRelativeChanges() {
        for ((bound, words) in listOf(
            "min" to listOf("مینیمم", "مینیموم", "مین", "حداقل", "کمترین مقدار", "پایین ترین سطح"),
            "max" to listOf("ماکزیمم", "ماکسیمم", "ماکسیموم", "مکس", "حداکثر", "بالاترین مقدار", "تا ته زیاد کن")
        )) for (word in words) {
            for ((side, zone) in listOf("" to "front", "شاگرد" to "passenger", "چپ" to "driver")) {
                for (noun in listOf("دما", "دمای اتاق", "دمای کولر", "درجه بخاری")) {
                    val text = "$noun $side $word"
                    val result = parser.recognize(text)
                    assertTrue("$text => $result", result is Recognition.Matched)
                    val command = (result as Recognition.Matched).command
                    assertEquals(text, "SET_CABIN_TEMPERATURE", command.action)
                    assertEquals(text, mapOf("zone" to zone, "temperatureLevel" to bound), command.parameters)
                    assertTrue(command.description.contains(if(bound=="max") "دما: حداکثر" else "دما: حداقل"))
                }
            }
        }
        same("دما ماکزیمم", "دما رو روی ماکزیمم بذار")
        same("دما مینیمم", "لطفا دما رو مینیمم کن")
        same("کولر مکس", "خیلی گرمه")
    }
    @Test fun firstCompleteCommandWinsInCompoundSpeech() {
        same("خیلی گرمه", "هوا خیلی گرمه کولر رو روشن کن")
        same("دمای راننده ۲۲", "راننده ۲۲ درجه و شاگرد ۲۴ درجه باشه")
        same("کولر روشن کن", "کولر رو روشن کن هوا خیلی گرمه")
        val firstCommands = listOf("خیلی گرمه", "خیلی سرده", "ماساژ شاگرد سطح ۳", "دمای راننده بیست و سه", "فن کم", "باد به صورت و پا")
        val secondCommands = listOf("کولر روشن کن", "سردکن راننده اکتیو", "فن ماکزیمم")
        for (first in firstCommands) for (second in secondCommands) {
            for (joiner in listOf(" ", " و ", " بعدش ", "، "))
                same(first, first + joiner + second)
        }
        same("ماساژ راننده روشن کن", "ماساژ راننده رو روشن کن و صندلی شاگرد رو گرم کن")
        same("خیلی گرمه", "خیلی گرمه و کولر روشن کن و ماساژ سطح سه")
        // Existing single-command numeric/dual-target grammar still takes precedence.
        same("دما ۲۳", "دما بیست و سه")
        same("باد به صورت و پا", "باد به صورت و پا بده")
        assertTrue(parser.recognize("ماساژ یه مدل دیگه باشه") is Recognition.Unknown)
    }
    @Test fun airflowFogAndFreshAirColloquialRequests() {
        fun command(text: String, action: String, vararg params: Pair<String, Any>) {
            val result = parser.recognize(text)
            assertTrue("$text => $result", result is Recognition.Matched)
            val c = (result as Recognition.Matched).command
            assertEquals(text, action, c.action)
            assertEquals(text, params.toMap(), c.parameters)
        }
        for ((direction, phrases) in listOf(
            "face" to listOf("جهت وزش باد به صورت", "وزش باد صورت", "جهت باد مستقیم", "باد بخوره به صورتم"),
            "feet" to listOf("جهت باد پایین", "جهت وزش باد پایین"),
            "windshield" to listOf("جهت باد بالا", "جهت وزش باد به شیشه جلو"),
            "all" to listOf("جهت باد در همه جهت", "جهت باد در همه جهات", "جهت باد در تمام جهات")
        )) for (phrase in phrases) {
            command(phrase, "SET_AIRFLOW_DIRECTION", "zone" to "front", "direction" to direction)
            command("لطفا $phrase", "SET_AIRFLOW_DIRECTION", "zone" to "front", "direction" to direction)
        }
        command("هوا رو عوض کن", "SET_AIR_RECIRCULATION", "mode" to "external")
        for (phrase in listOf("شیشه بخار گرفته", "شیشه جلو بخار گرفته", "بخار زدای شیشه جلو", "بخار زدایی شیشه جلو"))
            command(phrase, "SET_FRONT_DEFROSTER", "enabled" to true)
        for (phrase in listOf("شیشه عقب بخار گرفته", "بخار زدایی شیشه عقب", "بخار زدای شیشه عقب"))
            command(phrase, "SET_REAR_DEFROSTER", "enabled" to true)
        for (feature in listOf("کولر", "بخاری", "تهویه")) {
            for (phrase in listOf("همگام سازی $feature", "$feature همگام سازی", "دمای $feature یکسان شه", "دمای $feature مثل هم بشه", "$feature جلو همگام شه", "$feature جلو همگام شیشه"))
                command(phrase, "SET_FRONT_CLIMATE_SYNC", "enabled" to true)
        }
        for (n in listOf("۸", "۹", "12", "30", "1000", "999999999999"))
            command("سرعت فن $n", "SET_FAN_SPEED", "zone" to "front", "level" to "max")
        command("دور فن رو بذار رو سه", "SET_FAN_SPEED", "zone" to "front", "level" to 3)
        command("دور فن رو زیاد کن", "ADJUST_FAN_SPEED", "zone" to "front", "delta" to 1)
        command("دور فن رو کم کن", "ADJUST_FAN_SPEED", "zone" to "front", "delta" to -1)
        command("دور فن حداکثر", "SET_FAN_SPEED", "zone" to "front", "level" to "max")
        command("دور فن حداقل", "SET_FAN_SPEED", "zone" to "front", "level" to "min")
        command("سرعت فن شاگرد ۸", "SET_FAN_SPEED", "zone" to "passenger", "level" to "max")
    }
    @Test fun objectMarkerDoesNotHideContradictionsOrAdditionalCommands() {
        for (s in listOf(
            "سردکن صندلی و روشن کن و خاموش کن",
            "سردکن صندلی و گرمکن رو روشن کن", "سردکن صندلی راننده و شاگرد رو روشن کن",
            "سردکن صندلی عقب و روشن کن", "سردکن صندلی و پنجره رو روشن کن",
            "اگر سردکن صندلی و روشن کن", "سردکن صندلی و روشنه",
            "سردکن صندلی و پرتقال روشن کن", "فن و زیاد و کم کن"
        )) assertTrue("$s", parser.recognize(s) is Recognition.Unknown)
    }
}
