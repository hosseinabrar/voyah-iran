package com.abrar.raha

import com.abrar.voicecommands.Recognition
import com.abrar.voicecommands.VehicleCommand

/** Vehicle-independent intent vocabulary. No Android, OEM or CAN dependencies. */
enum class RahaAction {
    NAVIGATE_TO_SAVED_PLACE, OPEN_DEFAULT_MAP, OPEN_DEFAULT_MUSIC, OPEN_OEM_RADIO,
    SET_PEDESTRIAN_WARNING_SOUND, SET_CLIMATE_POWER, SET_AC_POWER, SET_MAX_COOLING, SET_CLIMATE_AUTO,
    ADJUST_CABIN_TEMPERATURE, ADJUST_FAN_SPEED, SET_AIRFLOW_DIRECTION,
    SET_AIR_RECIRCULATION, SET_FRONT_DEFROSTER, SET_REAR_DEFROSTER,
    SET_FRONT_CLIMATE_SYNC, SET_CABIN_TEMPERATURE, SET_FAN_SPEED,
    SET_SEAT_HEATING, SET_SEAT_VENTILATION, SET_SEAT_MASSAGE, SET_SEAT_MASSAGE_PATTERN,
    ADJUST_SEAT_HEATING, ADJUST_SEAT_VENTILATION, ADJUST_SEAT_MASSAGE,
    CONFIGURE_FRAGRANCE, SET_FRAGRANCE_DURATION, SET_FRAGRANCE_POWER, SET_FRAGRANCE_CHANNEL, SET_FRAGRANCE_INTENSITY, ADJUST_FRAGRANCE_INTENSITY,
    SET_WINDOW_POSITION, ADJUST_WINDOW_POSITION, ADJUST_SUNROOF_POSITION, ADJUST_SUNSHADE_POSITION,
    SET_DOOR_LOCK, OPEN_WINDOW, CLOSE_WINDOW, VENT_WINDOW, SET_CHILD_LOCK, OPEN_TAILGATE, CLOSE_TAILGATE,
    OPEN_CHARGE_PORT, OPEN_FUEL_FLAP, OPEN_SUNROOF, CLOSE_SUNROOF, SET_SUNROOF_POSITION, SET_SUNSHADE_POSITION,
    SET_HEADLIGHT_MODE, SET_AUTO_HIGH_BEAM, SET_DAYTIME_RUNNING_LIGHTS, SET_AMBIENT_LIGHT_MODE, SET_AMBIENT_LIGHT_POWER,
    SET_AMBIENT_LIGHT_BRIGHTNESS, ADJUST_AMBIENT_LIGHT_BRIGHTNESS, SET_REAR_FOG_LIGHT, SET_RAIN_SENSING_WIPERS,
    SET_SUSPENSION_HEIGHT, SET_DRIVE_MODE, SET_REGENERATION_LEVEL, ADJUST_REGENERATION_LEVEL, SET_HIGHWAY_SUSPENSION_ADAPTATION,
    SET_AUTO_HOLD, SET_HILL_DESCENT_CONTROL, SET_STABILITY_CONTROL, SET_PARKING_BRAKE, SET_WIPER_SENSITIVITY, ADJUST_WIPER_SENSITIVITY,
    SAVE_SEAT_POSITION, RECALL_SEAT_POSITION,
    SET_ENERGY_STRATEGY, SET_BATTERY_RESERVE_TARGET, STOP_CHARGING, SET_BATTERY_PRECONDITIONING,
    ACTIVATE_SCENE, DEACTIVATE_SCENE, SET_POWER_KEEP_DURATION, SET_POWER_KEEP_ENGINE_CHARGING, SET_MIRROR_FOLD,
    SET_DOOR_OPEN_WARNING, SET_FORWARD_COLLISION_WARNING, SET_REAR_CROSS_TRAFFIC_WARNING, SET_REAR_COLLISION_WARNING, SET_AUTOMATIC_EMERGENCY_BRAKING, SET_BLIND_SPOT_MONITORING, SET_LANE_SUPPORT_MODE, SET_DRIVER_MONITORING, SET_DRIVER_GESTURES, SET_LEAD_VEHICLE_DEPARTURE_ALERT, SET_FRONT_CROSS_TRAFFIC_WARNING, SET_TRAFFIC_SIGN_RECOGNITION, SET_LANE_DEPARTURE_ALERT_TYPE, SET_DISPLAY_BRIGHTNESS, SET_DISPLAY_THEME_MODE, SET_REAR_DISPLAY_LOCK, SET_AUDIO_VOLUME, ADJUST_AUDIO_VOLUME, SET_AUDIO_MUTE, PLAY_MEDIA, PAUSE_MEDIA, NEXT_MEDIA, PREVIOUS_MEDIA, OPEN_CAMERA, TAKE_PHOTO, START_VIDEO_RECORDING, STOP_VIDEO_RECORDING, SWITCH_CAMERA, SWAP_DISPLAY_APPS
}

/** Parses final transcripts only. An incomplete request never becomes an executable intent.
 * Unqualified climate requests target both front zones; unqualified seat requests target the driver. Explicit opposite targets are preserved.
 * Relative changes default to one step/degree. Enabling a seat feature does not invent a level.
 */
class RahaCommandRecognizer {
    fun recognize(raw: String): Recognition {
        if (RahaIgnoredSpeech.matches(raw)) return Recognition.Unknown("ignored non-command speech")
        RahaPhaseTwoRecognizer.memoryRestriction(normalize(raw))?.let { return Recognition.Unknown(it) }
        val whole = recognizeSingle(raw)
        if (whole is Recognition.Matched) return whole
        val words = normalize(raw).split(' ')
        // Find a complete leading command followed by another complete command.
        // Numbers are normalized first; choose the earliest complete command boundary.
        // Do not split a valid single command such as «باد به صورت و پا».
        for (end in 1..words.lastIndex) {
            val first = recognizeSingle(words.take(end).joinToString(" "))
            if (first !is Recognition.Matched) continue
            val tail = words.drop(end).dropWhile { it in commandConnectors }
            if (tail.isEmpty()) continue
            for (tailEnd in tail.size downTo 1) {
                if (recognizeSingle(tail.take(tailEnd).joinToString(" ")) is Recognition.Matched)
                    return first
            }
        }
        return whole
    }

    private fun recognizeSingle(raw: String): Recognition {
        // Keyword-test semantics: grammatical negation does not cancel an explicit action.
        val text = normalize(raw).split(' ').joinToString(" ") { keywordVerb[it] ?: borrowedWords[it] ?: it }
        if (text.isBlank()) return unknown("جمله‌ای شنیده نشد")
        if (Regex("(?:^| )(نکن\\S*|نذار|نگذار|نشه|نشود|نباشه|نباشد|نخوره|نزن|نبند|نمی\\S*|نخوا\\S*|اگر|اگه|چرا|آیا|جز|بجز|فقط نه)(?: |$)").containsMatchIn(text))
            return unknown("درخواست منفی، شرطی یا پرسشی اجرا نمی‌شود")
        if (Regex("(?:^| )(روشنه|خاموشه|فعاله|چنده|هست|نیست|کردی|میدونی)(?: |$)").containsMatchIn(text))
            return unknown("این جمله پرسش درباره وضعیت است")
        RahaAppLaunchRecognizer.recognize(text)?.let { return it }
        RahaNavigationRecognizer.recognize(text)?.let { return it }
        RahaPhaseSevenRecognizer.recognize(text)?.let { return it }
        RahaPhaseSixRecognizer.recognize(text)?.let { return it }
        RahaPhaseFiveRecognizer.recognize(text)?.let { return it }
        RahaPhaseFourRecognizer.recognize(text)?.let { return it }
        RahaPhaseThreeRecognizer.recognize(text)?.let { return it }
        RahaPhaseTwoRecognizer.recognize(text)?.let { return it }
        val candidates = mutableListOf<VehicleCommand>()
        var clarification: String? = null
        fun rule(action: RahaAction, parse: Scan.() -> Map<String, Any>?) {
            val scan = Scan(text)
            val result = scan.parse()
            if (scan.problem != null) clarification = scan.problem
            if (result != null && scan.finished()) candidates += VehicleCommand(
                action.name, action.name, result, describe(action, result))
        }
        fun climatePower(action: RahaAction, nouns: String, defaultOn: Boolean = false) = rule(action) {
            if (!take(nouns)) return@rule null
            val zone = zone(default = "front") ?: return@rule null
            if(action in setOf(RahaAction.SET_AC_POWER,RahaAction.SET_MAX_COOLING) && zone!="front") return@rule null
            val enabled = power() ?: if(defaultOn) true else return@rule null
            if(action==RahaAction.SET_CLIMATE_POWER && !enabled)take("دیگه باد نزنه")
            if(action==RahaAction.SET_AC_POWER && !enabled)take("فن کار کنه")
            mapOf("enabled" to enabled) + if(action == RahaAction.SET_CLIMATE_POWER || action == RahaAction.SET_CLIMATE_AUTO) mapOf("zone" to zone) else emptyMap()
        }
        rule(RahaAction.SET_CLIMATE_POWER) {
            if(!take("سیستم تهویه|تهویه اتاق|تهویه کابین|تهویه ماشین|تهویه هوا|تهویه|کولر|بخاری")) return@rule null
            val mode=last
            val zone=zone() ?: return@rule null
            val enabled=power() ?: if(mode=="کولر" && take("بگیر|بگیرین|بگیرید|بگیرم|بگیریم|بگیری|بگیره|گرفتن|بزن|بزنین|بزنید|بزنیم|بزنی|بزنه")) true else return@rule null
            if(!enabled)take("دیگه باد نزنه")
            buildMap {
                put("enabled",enabled);put("zone",zone)
                // These are desired-state constraints, not a reading of the car's current state.
                // The execution adapter must read the zone's setpoint before applying a bound.
                if(enabled) {
                    put("acEnabled",mode=="کولر" || mode=="بخاری")
                    if(mode=="کولر")put("temperatureMaxC",23.0)
                    if(mode=="بخاری")put("temperatureMinC",26.0)
                }
            }
        }
        for (maximum in listOf(false, true)) rule(if (maximum) RahaAction.SET_MAX_COOLING else RahaAction.SET_CLIMATE_POWER) {
            val weather = take("هوای اتاق|هوای کابین|هوای داخل|داخل ماشین|هوا|اتاق|کابین|داخل|ماشین")
            if (!take("گرمم شده|گرممه|گرمه|سردم شده|سردمه|سرده") && !(weather && take("گرم|سرد"))) return@rule null
            val cooling = last.startsWith("گرم")
            val very = take("خیلی")
            if (maximum != (cooling && very)) return@rule null
            take("هوای اتاق|هوای کابین|هوای داخل|هوا|اتاق|کابین|داخل|ماشین")
            val zone = zone() ?: return@rule null
            if (maximum) {
                if (zone != "front") return@rule null
                mapOf("enabled" to true)
            } else {
                mapOf("enabled" to true, "zone" to zone, "acEnabled" to true) +
                    when {
                        cooling -> mapOf("temperatureMaxC" to 23.0)
                        very -> mapOf("temperatureLevel" to "max")
                        else -> mapOf("temperatureMinC" to 26.0)
                    }
            }
        }
        climatePower(RahaAction.SET_AC_POWER, "کمپرسور کولر|کمپرسور|ای سی کولر|ای سی ماشین|ای سی|ac")
        climatePower(RahaAction.SET_MAX_COOLING, "حداکثر سرمایش|سرمایش حداکثری|خنک سازی حداکثری|مکس ای سی|مکس کولر|کولر مکس|max ac", true)
        rule(RahaAction.SET_MAX_COOLING) {
            if (!take("کولر") || !take("تا ته|تا آخر|حداکثر|مکس|max")) return@rule null
            if (zone() != "front") return@rule null
            take("زیاد|بالا|بیشتر")
            val enabled = power() ?: true
            mapOf("enabled" to enabled)
        }
        rule(RahaAction.SET_CLIMATE_AUTO) {
            if (!take("تهویه اتاق|تهویه کابین|تهویه ماشین|تهویه|کولر|بخاری") || !take("خودکار|اتوماتیک|اتومات|اتو|auto|دستی")) return@rule null
            val manual = last == "دستی"
            val zone = zone() ?: return@rule null
            take("در بیار|دربیار") // Enter automatic mode; not the generic off verb.
            val on = power() ?: !manual
            if(manual && on) return@rule null
            mapOf("enabled" to on, "zone" to zone)
        }
        for(relative in listOf(true, false)) rule(if(relative) RahaAction.ADJUST_CABIN_TEMPERATURE else RahaAction.SET_CABIN_TEMPERATURE) {
            val subject = take("دمای اتاق|دمای داخل|دمای کابین|دمای تهویه|دمای کولر|دمای بخاری|درجه بخاری|درجه کولر|درجه تهویه|دمای|دما|تهویه|کولر|بخاری")
            val coolingStrength = subject && last == "کولر"
            val explicitTemperature = subject && (last.startsWith("دما") || last.startsWith("درجه"))
            val zone = zone() ?: return@rule null
            val bound = levelBound()
            if (bound != null) {
                if (relative || !explicitTemperature) return@rule null
                direction()
                levelUnits()
                if (number() != null) return@rule null
                return@rule mapOf("zone" to zone, "temperatureLevel" to bound)
            }
            // More cooling means a lower setpoint; explicitly named temperature
            // (e.g. «دمای کولر») retains its ordinary numeric direction.
            val direction = direction()?.let { if (coolingStrength) -it else it }
            val thermal = take("گرم تر|گرمتر|خنک تر|خنکتر|سرد تر|سردتر")
            val thermalDirection = if(thermal) if(last.startsWith("گرم")) 1 else -1 else null
            if(!subject && !thermal && !contains("درجه")) return@rule null
            if(direction != null && thermalDirection != null && direction != thermalDirection) return@rule null
            val deltaSign = direction ?: thermalDirection
            val value = number()
            take("درجه")
            if(relative) {
                if(deltaSign == null) return@rule null
                val n = value ?: 1.0
                if(n <= 0 || n > 30 || n*2%1 != 0.0) return@rule null
                mapOf("zone" to zone, "delta" to (n*deltaSign))
            } else {
                if(deltaSign != null || value == null || value !in 16.0..30.0 || value*2%1 != 0.0) return@rule null
                mapOf("zone" to zone, "degreesC" to value)
            }
        }
        for(relative in listOf(true,false)) rule(if(relative) RahaAction.ADJUST_FAN_SPEED else RahaAction.SET_FAN_SPEED) {
            if(!take("دور فن کولر|دور فن|سرعت فن کولر|فن کولر|فن تهویه|سرعت فن|شدت باد کولر|شدت باد|باد تهویه|باد کولر|قدرت باد|فن|باد")) return@rule null
            val zone=zone() ?: return@rule null
            val extreme=levelBound()
            val d=direction()
            val n=number();levelUnits();take("تا")
            if(extreme != null) {
                if(relative || n != null) return@rule null
                mapOf("zone" to zone,"level" to extreme)
            } else if(relative) {
                if(d==null) return@rule null
                val v=n ?: 1.0
                if(v !in 1.0..7.0 || v%1!=0.0) return@rule null
                mapOf("zone" to zone,"delta" to (v.toInt()*d))
            } else {
                if(d!=null || n==null || n < 1.0 || n%1!=0.0) return@rule null
                mapOf("zone" to zone,"level" to if(n > 7.0) "max" else n.toInt())
            }
        }
        rule(RahaAction.SET_AIRFLOW_DIRECTION) {
            if(!take("دریچه های باد|دریچه باد|دریچه تهویه|دریچه کولر|دریچه|جهت وزش باد|وزش باد|جهت باد|باد تهویه|باد کولر|باد|تهویه|حالت")) return@rule null
            val all=take("همه جهت ها|همه جهت|همه جهات|تمام جهات|تمام جهت ها|همه طرف ها|همه طرف|همه")
            // Consume the complete windshield phrase before the face alias «جلو».
            val glass=take("شیشه جلو|شیشه|بالا")
            val face=take("رو به رو|روبه رو|روبهرو|روبرو|رو برو|صورتم|صورت|مستقیم|جلو")
            val feet=take("پایین پام|پاها|پا|پام|پایین")
            if(power()==false) return@rule null
            if(!all && !face && !feet && !glass || !all && face && glass) return@rule null
            val zone=zone() ?: return@rule null
            val value=when {all->"all";face && feet->"faceAndFeet";glass && feet->"windshieldAndFeet";face->"face";feet->"feet";else->"windshield"}
            take("هم");take("هم");take("و")
            mapOf("direction" to value,"zone" to zone)
        }
        rule(RahaAction.SET_AIR_RECIRCULATION) {
            if(!take("تهویه هوای|تهویه هوا|گردش هوای|گردش هوا|گردش|ورود هوای|ورودی هوای|هوای|هوا|تهویه")) return@rule null
            val circulationSubject=last.startsWith("گردش")
            if (take("عوض|تعویض|تازه کن")) return@rule mapOf("mode" to "external")
            take("تهویه")
            take("گردش")
            val internal=take("داخل|داخلی"); val external=take("بیرون|بیرونی|خارج|خارجی|تازه"); val auto=take("خودکار|اتومات|اتوماتیک")
            val enabled=power()
            if(auto) {take("و"); if(enabled==false || !circulationSubject) return@rule null; mapOf("mode" to "auto")}
            else {
                if(internal==external) return@rule null
                val inside = if(enabled==false) !internal else internal
                mapOf("mode" to if(inside) "internal" else "external")
            }
        }
        for(front in listOf(true,false)) rule(if(front) RahaAction.SET_FRONT_DEFROSTER else RahaAction.SET_REAR_DEFROSTER) {
            if(!take("بخار زدایی|بخارزدایی|بخار زدای|بخار زدا|بخارزدای|بخارزدا|بخارگیر|بخار گیر|دیفراست|دیفراستر|بخار|گرم کن|گرمکن")) return@rule null
            val heater=last in listOf("گرم کن","گرمکن")
            val bareFog=last=="بخار"
            val glass=take("شیشه")
            if(heater && !glass) return@rule null
            val rear=take("عقب"); val explicitFront=take("جلو")
            if(rear && explicitFront || front == rear) return@rule null
            if(bareFog && !glass && !rear && !explicitFront) return@rule null
            val enabled=power()
            take("بگیر|گرفتن|گرفته|کرده")
            take("بخار کرده");take("بخارزدا")
            if (heater && enabled == null) return@rule null
            mapOf("enabled" to (enabled ?: true))
        }
        rule(RahaAction.SET_FRONT_CLIMATE_SYNC) {
            if(!take("دمای کابین|دمای اتاق|دمای داخل ماشین|دمای داخل خودرو|دمای داخل|دمای ماشین|دمای خودرو|دمای کولر|دمای بخاری|دمای تهویه|همگام سازی|دمای|دما|تنظیم|کولر|بخاری|تهویه|سینک")) return@rule null
            var sync=last=="سینک" || last=="همگام سازی"
            sync=take("همگام سازی|مثل هم|سینک|هماهنگ|همگام|یکسان|یکی|جدا") || sync
            val separate=last=="جدا"
            if(!sync) return@rule null
            take("تهویه|کولر|بخاری")
            // Scoped ASR repair: «همگام شیشه» commonly comes from «همگام شه».
            // A real window noun remains meaningful in all other command families.
            take("شیشه")
            take("راننده و شاگرد|راننده و همراه|چپ و راست|دو طرف|2 طرف|هر دو طرف|هر 2 طرف")
            take("جلو")
            val on=power() ?: !separate
            if(separate && on) return@rule null
            mapOf("enabled" to on)
        }
        for(feature in listOf("heating","ventilation","massage")) rule(when(feature){"heating"->RahaAction.SET_SEAT_HEATING;"ventilation"->RahaAction.SET_SEAT_VENTILATION;else->RahaAction.SET_SEAT_MASSAGE}) {
            val aliases=when(feature){"heating"->"گرم کن|گرمکن|گرمایش|گرم";"ventilation"->"سرد کن|سردکن|تهویه|فن|خنک کن|خنک";else->"ماساژور|ماساژم|ماساژ"}
            if(!take(aliases)) return@rule null
            val thermal=last in listOf("گرم","خنک","گرم کن","خنک کن")
            val needsSeatNoun=last in listOf("تهویه","فن","گرم","خنک")
            val hasSeat=take("صندلی|صندلیم")
            if(needsSeatNoun && !hasSeat) return@rule null
            val seat=seat() ?: return@rule null
            val on=power()
            val bound=levelBound()
            if (bound != null) {
                direction() // «تا ته زیاد» still means the absolute ceiling, not +1.
                levelUnits()
                if (on == false || number() != null) return@rule null
                return@rule mapOf("seat" to seat, "enabled" to true, "level" to bound)
            }
            val level=number();levelUnits()
            if(level!=null && (level !in 0.0..3.0 || level%1!=0.0)) return@rule null
            if(on==false && level!=null && level!=0.0 || on==true && level==0.0) return@rule null
            if(on==null && level==null && !(thermal && hasRequest() || feature=="massage" && hasRequest())) return@rule null
            val enabled=on ?: (level!=0.0)
            buildMap {put("seat",seat);put("enabled",enabled);if(level!=null)put("level",level.toInt()) else if(!enabled)put("level",0)}
        }
        for ((noun, action) in listOf(
            "گرم کن|گرمکن|گرمایش" to RahaAction.ADJUST_SEAT_HEATING,
            "سرد کن|سردکن|تهویه|فن|خنک کن" to RahaAction.ADJUST_SEAT_VENTILATION,
            "ماساژور|ماساژ" to RahaAction.ADJUST_SEAT_MASSAGE
        )) rule(action) {
            if (!take(noun)) return@rule null
            val needsSeat = last in listOf("تهویه", "فن")
            val hasSeat = take("صندلی|صندلیم")
            if (needsSeat && !hasSeat) return@rule null
            val seat = seat() ?: return@rule null
            val sign = direction() ?: return@rule null
            val amount = number() ?: 1.0
            take("سطح|درجه|شدت|پله")
            if (amount !in 1.0..3.0 || amount % 1 != 0.0) return@rule null
            // Relative intent only: the vehicle adapter will read and clamp the actual level.
            mapOf("seat" to seat, "delta" to amount.toInt() * sign)
        }
        rule(RahaAction.SET_SEAT_MASSAGE_PATTERN) {
            if(!take("ماساژور|ماساژ")) return@rule null
            take("صندلی")
            val seat=seat() ?: return@rule null
            val pattern=when {take("موجی|موج|ویوینگ|ویو")->"wave";take("غلتکی|غلطکی|رولینگ|چرخشی|چرخش|دایره ای")->"rolling";else->return@rule null}
            if (power() == false) return@rule null
            mapOf("seat" to seat,"pattern" to pattern)
        }
        // A spatial target with plain «باد» describes direction, not fan speed.
        // Explicit fan/strength nouns continue to use the fan rules.
        if (candidates.any { it.action == RahaAction.SET_AIRFLOW_DIRECTION.name } &&
            text.split(' ').none { it in setOf("فن", "سرعت", "شدت", "قدرت", "دور") }) {
            candidates.removeAll { it.action in setOf(RahaAction.ADJUST_FAN_SPEED.name, RahaAction.SET_FAN_SPEED.name) }
        }
        val unique=candidates.distinctBy {it.action to it.parameters}
        return when(unique.size) {1->Recognition.Matched(unique.single());0->unknown(clarification ?: "فرمان پشتیبانی‌شده‌ای تشخیص داده نشد");else->unknown("جمله بیش از یک برداشت دارد؛ هر بار یک فرمان بگویید")}
    }

    internal class Scan(text:String) {
        // Resolve the compound target before any rule consumes the word driver.
        private val tokens=text.replace(Regex("""(?<!\S)کمک راننده(?!\S)"""), "شاگرد").split(' ').toMutableList()
        private val original=text
        var last=""; private set
        var problem:String?=null
        fun contains(word:String)=word in tokens
        fun take(aliases:String):Boolean {
            for(alias in aliases.split('|').sortedByDescending {it.length}) {
                val parts=alias.split(' ')
                for(i in 0..tokens.size-parts.size) if(parts.indices.all {tokens[i+it]==parts[it]}) {
                    parts.indices.forEach {tokens[i+it]=""};last=alias;return true
                }
            };return false
        }
        fun power():Boolean? {
            val off=take("غیر فعال|غیرفعال|خاموش|قطع|ببند|بسته|متوقف|توقف|بردار|بر دار|خارج شو|دربیار|در بیار|برداشتن")
            val on=take("روشن|فعال|راه بنداز|راه بینداز|راه اندازی|راه بنداختن|روشن کردن|بزن|باز")
            if(off && on) {problem="روشن و خاموش هم‌زمان درخواست شده";tokens += "CONFLICT";return null}
            return if(off) false else if(on) true else null
        }
        fun levelUnits() {
            while (take(RahaLevelVocabulary.units.joinToString("|"))) { /* optional nouns */ }
        }
        fun levelBound(): String? {
            // Consume the specific «تا ته کم» before the general «تا ته».
            val minimum = take(RahaLevelVocabulary.minimum.joinToString("|"))
            val maximum = take(RahaLevelVocabulary.maximum.joinToString("|"))
            if (minimum && maximum) { problem="دو حد متفاوت مشخص شده"; return null }
            return if (minimum) "min" else if (maximum) "max" else null
        }
        fun direction():Int? {
            take("کمی")
            val up=take("بیشتر|زیاد|بالا|افزایش|تندتر|تند تر")
            val down=take("کمتر|کم|پایین|کاهش|آروم تر|آرومتر|آرام تر|آرامتر")
            if(up && down) {tokens+="CONFLICT";return null}
            return if(up)1 else if(down)-1 else null
        }
        fun zone(default:String="front"):String? {
            if(take("عقب|پشت|ردیف دوم|ردیف 2")){problem="وویا فری کنترل مستقل تهویه عقب ندارد";return null}
            val left=take("راننده|چپ"); val right=take("شاگرد|همراه|راست|بغل|سرنشین جلو")
            val front=take("جلو")
            if(left && right) {if(!take("و"))return null;return "front"}
            val all=take("همه|دو طرف|2 طرف")
            if(all && (left || right)){problem="هدف هم‌زمان تکی و همه مشخص شده";return null}
            return if(left) "driver" else if(right) "passenger" else if(front || all) "front" else default
        }
        fun seat():String? {
            if(take("عقب|پشت|ردیف دوم|ردیف 2")){problem="فقط صندلی‌های جلو پشتیبانی می‌شوند";return null}
            val left=take("راننده|چپ"); val right=take("شاگرد|همراه|راست|بغل|سرنشین جلو")
            take("جلو")
            if(left && right){problem="هر بار یک صندلی را مشخص کنید";return null}
            return if(right) "passenger" else "driver"
        }
        fun number():Double? {
            val found=tokens.indices.filter {tokens[it].matches(Regex("[0-9]+(?:\\.[0-9]+)?"))}
            if(found.size!=1)return null
            val i=found.single();val n=tokens[i].toDouble();tokens[i]="";return n
        }
        fun hasRequest()=Regex("(?:^| )(کن|کنید|کنین|کنی|بکن|بکنید|بده|بذار|بگذار|بزار|باشه|بشه|ببر|بیار|بخوره|بچرخد|بچرخه|بیاد|کار|انتخاب|تنظیم|کردن|خارج)(?: |$)").containsMatchIn(original)
        // Recognition depends on the consumed semantic slots, not auxiliary verbs
        // or object markers (including ASR's «و» spelling of colloquial /o/).
        fun finished() = problem == null && tokens.all { it.isBlank() || it in filler || it in optionalSpokenWords }

    }

    companion object {
        private val optionalSpokenWords = setOf("شود", "سازی", "بصورت", "صورت", "شکل", "انجام", "بوزه", "بوزد", "وزش", "وضعیت", "کمی", "بکش", "بکشید", "بکشین", "بکِش", "بدهید", "بدین", "بدید", "بیارید", "بیارین", "ببرید", "ببرین", "بذارید", "بذارین", "بزارید", "بزارین")
        private val commandConnectors = setOf("و", "بعد", "بعدش", "سپس", "حالا")
        private fun unknown(reason:String)=Recognition.Unknown(reason)
        private val borrowedWords = mapOf(
            "اکتیو" to "فعال",
            "هیتر" to "بخاری", "لول" to "سطح", "لیول" to "سطح",
            "اسپید" to "سرعت", "اوتو" to "اتو"
        )
        private val keywordVerb = mapOf(
            "نکن" to "کن", "نکنید" to "کنید", "نکنین" to "کنین",
            "نکنی" to "کنی", "نکنه" to "کنه", "نکنن" to "کن",
            "نذار" to "بذار", "نگذار" to "بگذار", "نشه" to "بشه",
            "نشود" to "بشود", "نباشه" to "باشه", "نباشد" to "باشد"
        )
        private val filler=setOf("رها","لطفا","خواهشا","میشه","می","شه","کنی","کن","کنید","کنین","کنن","بکن","بکنید","رو","را","ر","و","برای","به","در","روی","تو","از","حالت","مد","مود","دکمه","قرار","انتخاب","تنظیم","تنظیمات","سمت","جهت","طرف","بده","بذار","بزار","بگذار","بشه","بشود","باشه","باشد","بیار","ببر","کردن","کرد","خیلی","اینجا","میخوام","میخواهم","خوام","خواهم","میخواهیم","میخوایم","بخوره","بچرخه","بیاد","کار","کنه","هم","دیگه","فقط","بگیر","ها","های")
        fun normalize(raw:String):String {
            var s=raw.lowercase().replace('ي','ی').replace('ك','ک').replace('ۀ','ه')
                .replace(Regex("[\\u064B-\\u065F\\u0670]"),"").replace(Regex("[\\u200c\\u200e\\u200f]")," ")
            s=s.map {when(it){in '۰'..'۹'->'0'+(it-'۰');in '٠'..'٩'->'0'+(it-'٠');'٫'->'.';else->it}}.joinToString("")
            s=s.replace(Regex("[،,!?؟؛:]+|(?<!\\d)\\.|\\.(?!\\d)")," ").replace(Regex("\\s+")," ").trim()
            s=s.replace("دی اکتیو","غیر فعال").replace("دیاکتیو","غیر فعال").replace("دیز اکتیو","غیر فعال")
            s=s.replace("اچ دی سی","hdc").replace("ای اس سی","esc")
            s=s.replace("می شه","میشه").replace(Regex("(?<!\\S)ای سی(?!\\S)"),"AC_TOKEN")
            s=s.replace("تا انتها", "تا ته").replace(Regex("(?<!\\S)مد(?!\\S)"), "مود")
            s=s.replace(Regex("(?<!\\S)(?:استوپ|استاپ)(?!\\S)"), "قطع")
            s=RahaFormalVocabulary.normalize(s)
            s=RahaColloquialVocabulary.normalize(s)
            s=s.replace(Regex("(?<=بیست )و(?=یک|دو|سه|چهار|پنج|شش|هفت|هشت|نه)"),"و ")
            val numbers=mapOf("بیست و نه" to "29","بیست و هشت" to "28","بیست و هفت" to "27","بیست و شش" to "26","بیست و پنج" to "25","بیست و چهار" to "24","بیست و سه" to "23","بیست و دو" to "22","بیست و یک" to "21","سی" to "30","بیست" to "20","نوزده" to "19","هجده" to "18","هفده" to "17","شانزده" to "16","پانزده" to "15","چهارده" to "14","سیزده" to "13","دوازده" to "12","یازده" to "11","ده" to "10","نه" to "9","هشت" to "8","هفت" to "7","شش" to "6","پنج" to "5","چهار" to "4","سوم" to "3","دوم" to "2","اول" to "1","سه" to "3","دو" to "2","یک" to "1","یه" to "1","صفر" to "0")
            for((word,n) in numbers)s=s.replace(Regex("(?<!\\S)$word(?!\\S)"),n)
            s=s.replace(Regex("(?<!\\S)(\\d+) و نیم(?!\\S)")){"${it.groupValues[1]}.5"}
            return s.replace("AC_TOKEN","ای سی")
        }
        private fun describe(action:RahaAction,p:Map<String,Any>):String {
            val name=when(action){
                RahaAction.SET_CLIMATE_POWER->"تهویه";RahaAction.SET_AC_POWER->"کمپرسور کولر";RahaAction.SET_MAX_COOLING->"سرمایش حداکثری";RahaAction.SET_CLIMATE_AUTO->"تهویه خودکار"
                RahaAction.ADJUST_CABIN_TEMPERATURE->"تغییر دما";RahaAction.SET_CABIN_TEMPERATURE->"دمای کابین";RahaAction.ADJUST_FAN_SPEED->"تغییر سرعت فن";RahaAction.SET_FAN_SPEED->"سرعت فن"
                RahaAction.SET_AIRFLOW_DIRECTION->"جهت باد";RahaAction.SET_AIR_RECIRCULATION->"گردش هوا";RahaAction.SET_FRONT_DEFROSTER->"بخارزدای جلو";RahaAction.SET_REAR_DEFROSTER->"بخارزدای عقب"
                RahaAction.SET_FRONT_CLIMATE_SYNC->"همگام‌سازی تهویه جلو";RahaAction.SET_SEAT_HEATING->"گرم‌کن صندلی";RahaAction.SET_SEAT_VENTILATION->"تهویه صندلی";RahaAction.SET_SEAT_MASSAGE->"ماساژ صندلی";RahaAction.SET_SEAT_MASSAGE_PATTERN->"الگوی ماساژ";RahaAction.ADJUST_SEAT_HEATING->"تغییر شدت گرم‌کن صندلی";RahaAction.ADJUST_SEAT_VENTILATION->"تغییر شدت سردکن صندلی";RahaAction.ADJUST_SEAT_MASSAGE->"تغییر شدت ماساژ";else->action.name
            }
            val target=when(p["seat"] ?: p["zone"]){"driver"->"راننده (چپ)";"passenger"->"شاگرد (راست)";"front"->"دو سمت جلو";else->""}
            val labels=mapOf("max" to "حداکثر","min" to "حداقل","wave" to "موجی","rolling" to "غلتکی","circular" to "چرخشی","internal" to "هوای داخل","external" to "هوای بیرون","auto" to "خودکار","face" to "صورت","feet" to "پا","faceAndFeet" to "صورت و پا","windshield" to "شیشه جلو","windshieldAndFeet" to "شیشه جلو و پا","all" to "همه جهت‌ها")
            val values=p.filterKeys{it!="seat" && it!="zone"}.map {(k,v)->when(k){"enabled"->if(v==true)"روشن" else "خاموش";"acEnabled"->if(v==true)"AC روشن" else "AC خاموش";"temperatureMaxC"->"دما فقط اگر بالاتر از $v است به $v برسد";"temperatureMinC"->"دما فقط اگر پایین‌تر از $v است به $v برسد";"delta"->"تغییر ${if((v as Number).toDouble()>0)"+" else ""}$v";"degreesC"->"$v درجه";"temperatureLevel"->"دما: ${labels[v.toString()] ?: v}";"level"->"شدت ${labels[v.toString()] ?: v}";else->labels[v.toString()] ?: v.toString()}}.joinToString("، ")
            return "$name $target: $values"
        }
    }
}
