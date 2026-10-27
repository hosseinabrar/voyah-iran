package com.abrar.raha.intent

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.abrar.voicecommands.Recognition
import com.abrar.voicecommands.VehicleCommand
import org.json.JSONArray
import org.json.JSONObject
import java.nio.LongBuffer
import java.security.MessageDigest
import kotlin.math.exp
import kotlin.math.round

/** One model, one CPU session. Caller must prepare/infer/close off the UI thread.
 * No legacy intent recognizer or network fallback exists here.
 */
class RahaIntentModel(context: Context) : AutoCloseable {
    private val app = context.applicationContext
    private val protectedAssets = RahaProtectedAssets(app)
    private var session: OrtSession? = null
    private lateinit var tokenizer: RahaWordPiece
    private lateinit var schema: JSONObject
    private var frames = emptyList<String>()
    private var slotKeys = emptyList<String>()
    private var contactRules = emptyList<Pair<String,String>>()
    private val values = mutableMapOf<String,List<Any>>()
    private val serializedValues = mutableMapOf<String,List<String>>()
    private var closed = false

    @Synchronized fun prepare() {
        check(!closed)
        if(session!=null)return
        val manifest=json("manifest.json")
        schema=json("schema.json")
        frames=schema.getJSONArray("frames").strings()
        val actions=com.abrar.raha.RahaAction.entries.map { it.name }.toSet()+setOf("UNKNOWN","CALL_CONTACT","CONFIRM_CALL","CANCEL_DIALOG")
        require(frames.all { it.substringBefore('|') in actions }) { "Unsupported intent schema action" }
        slotKeys=manifest.getJSONArray("outputs").strings().drop(1).map { it.removePrefix("slot_") }
        for(key in slotKeys) {
            val strings=schema.getJSONObject("slots").getJSONArray(key).strings()
            serializedValues[key]=strings
            values[key]=strings.map { JSONArray("[$it]").get(0) }
        }
        contactRules=run {
            val array=JSONArray(protectedAssets.text("contact-rules.json"))
            (0 until array.length()).map { array.getJSONObject(it).let { rule -> rule.getString("before") to rule.getString("after") } }
        }
        tokenizer=RahaWordPiece(protectedAssets.text("vocab.txt").trimEnd('\r','\n').lines())
        protectedAssets.removeLegacyPlaintextCache()
        OrtSession.SessionOptions().use { options ->
            options.setIntraOpNumThreads(2)
            options.setInterOpNumThreads(1)
            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            val model=protectedAssets.read("model.onnx")
            val created=try {
                check(MessageDigest.getInstance("SHA-256").digest(model).joinToString(""){"%02x".format(it)}==manifest.getString("model_sha256")) { "Intent model checksum mismatch" }
                OrtEnvironment.getEnvironment().createSession(model,options)
            } finally { model.fill(0) }
            try {
                require(created.inputNames==setOf("input_ids","attention_mask","token_type_ids"))
                require(created.outputNames==manifest.getJSONArray("outputs").strings().toSet())
                session=created
                recognize("سلام") // Warm allocations before enabling microphone capture.
            } catch(t:Throwable) { session=null;created.close();throw t }
        }
    }

    @Synchronized fun recognize(raw:String):Recognition {
        check(!closed)
        val active=checkNotNull(session) { "Intent model not prepared" }
        val text=RahaWordPiece.normalize(raw)
        if(text.isBlank())return Recognition.Unknown("متنی دریافت نشد")
        val places=com.abrar.raha.RahaPlaces.mentions(text)
        val hasPlaceRequest=com.abrar.raha.RahaPlaces.requests.any { (" $text ").contains(" $it ") }
        val customPlaces=if(hasPlaceRequest)places.filter { it.first.startsWith("user_") } else emptyList()
        if(customPlaces.map { it.first }.distinct().size>1)return Recognition.Unknown("بیش از یک مقصد گفته شده است")
        // User-saved place names are dynamic entities, not extra hardcoded intents.
        val modelText=customPlaces.fold(text) { value,(_,name) -> value.replace(name,"شاهپری") }
        val encoded=tokenizer.encode(modelText)
        if(encoded.count>48)return Recognition.Unknown("یک فرمان کوتاه بگو")
        if(Regex("(?U)\\b(?:نکن|نبند|نذار|اگر|اگه|آیا|چرا)\\b").containsMatchIn(text) || '?' in text || '؟' in text)
            return Recognition.Unknown("فرمان منفی، شرطی یا پرسشی مشخص نیست")
        val env=OrtEnvironment.getEnvironment()
        val shape=longArrayOf(1,encoded.ids.size.toLong())
        OnnxTensor.createTensor(env,LongBuffer.wrap(encoded.ids),shape).use { ids ->
            OnnxTensor.createTensor(env,LongBuffer.wrap(LongArray(encoded.ids.size){1}),shape).use { mask ->
                OnnxTensor.createTensor(env,LongBuffer.wrap(LongArray(encoded.ids.size)),shape).use { types ->
                    active.run(mapOf("input_ids" to ids,"attention_mask" to mask,"token_type_ids" to types)).use { output ->
                        fun probabilities(name:String):List<Double> {
                            val tensor=output.get(name).orElseThrow { IllegalStateException("Missing output $name") } as OnnxTensor
                            val buffer=tensor.floatBuffer
                            val logits=FloatArray(buffer.remaining());buffer.get(logits)
                            require(logits.all { it.isFinite() }) { "Invalid model logits" }
                            val max=logits.max();val exps=logits.map { exp((it-max).toDouble()) };val sum=exps.sum()
                            return exps.map { it/sum }
                        }
                        val scores=probabilities("frames")
                        val ranked=scores.indices.sortedByDescending { scores[it] }
                        val frame=frames[ranked[0]];val action=frame.substringBefore('|')
                        fun rounded(value:Double)=round(value*10000)/10000
                        if(action=="UNKNOWN" || !RahaExecutionConfidence.isDisplayedHundred(scores[ranked[0]]) || rounded(scores[ranked[0]])-rounded(scores[ranked[1]])<.15)
                            return Recognition.Unknown("منظور فرمان مشخص نیست")
                        val params=linkedMapOf<String,Any>();val confidences=mutableMapOf<String,Double>()
                        val allowed=mutableMapOf<String,List<Any>>()
                        val fields=schema.getJSONObject("allowed").optJSONObject(frame)
                        for(key in frame.substringAfter('|').split(',').filter { it.isNotEmpty() }) {
                            val probs=probabilities("slot_$key")
                            val permitted=fields?.optJSONArray(key)?.strings() ?: serializedValues.getValue(key)
                            val indices=permitted.map { serializedValues.getValue(key).indexOf(it).also { id -> require(id>=0) } }
                            val index=indices.maxBy { probs[it] }
                            params[key]=values.getValue(key)[index];confidences[key]=rounded(probs[index])
                            allowed[key]=indices.map { values.getValue(key)[it] }
                        }
                        val grounded=RahaIntentGrounding.apply(text,action,params,allowed)
                        grounded.error?.let { return Recognition.Unknown(it) }
                        if(confidences.any { (key,score) -> key !in grounded.grounded && score<.65 })return Recognition.Unknown("یکی از مقادیر فرمان مشخص نیست")
                        val result=grounded.parameters.toMutableMap()
                        if(customPlaces.isNotEmpty()) {
                            if(action!="NAVIGATE_TO_SAVED_PLACE")return Recognition.Unknown("فرمان مکان ذخیره شده مشخص نیست")
                            result["destination"]=customPlaces.first().first
                        }
                        if(action=="CALL_CONTACT") {
                            val name=RahaContactSpan.extract(text,contactRules)
                                ?: return Recognition.Unknown("نام مخاطب مشخص نیست")
                            result["contact_query"]=com.abrar.raha.RahaDefaultContacts.find(com.abrar.raha.RahaContacts.normalize(name))?.name ?: name
                        }
                        if(action in setOf("CONFIRM_CALL","CANCEL_DIALOG"))return Recognition.Unknown("تماسی در انتظار پاسخ نیست")
                        return Recognition.Matched(VehicleCommand(action,action,result,schema.getJSONObject("titles").optString(action,action)))
                    }
                }
            }
        }
    }
    private fun json(name:String)=JSONObject(protectedAssets.text(name))
    private fun JSONArray.strings()=(0 until length()).map(::getString)
    @Synchronized override fun close() { closed=true;session?.close();session=null }
}
