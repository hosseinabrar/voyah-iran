package com.abrar.voyahvoicetest

import android.content.Context
import ai.onnxruntime.*
import com.abrar.raha.intent.*
import org.json.JSONArray
import org.json.JSONObject
import java.nio.LongBuffer
import java.security.MessageDigest
import kotlin.math.exp
import kotlin.math.round

/** Preview only: reports the winning candidate even below the vehicle execution gate.
 * This class is packaged only in the test app; production gates remain untouched. */
class TesterModel(context: Context) : AutoCloseable {
    private val assets=RahaProtectedAssets(context)
    private lateinit var session: OrtSession
    private lateinit var tokenizer: RahaWordPiece
    private lateinit var schema: JSONObject
    private lateinit var frames: List<String>
    private var contacts=emptyList<Pair<String,String>>()
    lateinit var version:String; private set
    lateinit var catalog:JSONObject; private set
    fun prepare() {
        val manifest=JSONObject(assets.text("manifest.json"))
        version=manifest.getString("model_sha256")
        schema=JSONObject(assets.text("schema.json"));frames=schema.getJSONArray("frames").strings()
        tokenizer=RahaWordPiece(assets.text("vocab.txt").trimEnd('\r','\n').lines())
        val rules=JSONArray(assets.text("contact-rules.json"))
        contacts=(0 until rules.length()).map { rules.getJSONObject(it).let { r->r.getString("before") to r.getString("after") } }
        val titles=JSONObject(schema.getJSONObject("titles").toString())
        titles.keys().asSequence().toList().forEach { k->titles.put(k,titles.getString(k).trim().replace(Regex(" (?:راننده \\(چپ\\)|شاگرد \\(راست\\)|دو سمت جلو|راننده|شاگرد|مرکزی)$"),"")) }
        mapOf("CONFIGURE_FRAGRANCE" to "تنظیم ترکیبی رایحه","NAVIGATE_TO_SAVED_PLACE" to "مسیریابی به مکان ذخیره‌شده",
            "SET_FRAGRANCE_CHANNEL" to "انتخاب رایحه","SET_FRAGRANCE_DURATION" to "مدت رایحه","SET_FRAGRANCE_INTENSITY" to "شدت رایحه",
            "SET_FRAGRANCE_POWER" to "روشن یا خاموش کردن رایحه","SET_LANE_SUPPORT_MODE" to "کمک بین خطوط",
            "SET_AUDIO_VOLUME" to "تنظیم بلندی صدا","ADJUST_AUDIO_VOLUME" to "کم یا زیاد کردن بلندی صدا",
            "SET_AUDIO_MUTE" to "بی‌صدا کردن یا بازگرداندن صدا").forEach { (k,v)->titles.put(k,v) }
        val variants=JSONObject()
        for(frame in frames){
            val action=frame.substringBefore('|');val fields=JSONObject()
            for(key in frame.substringAfter('|').split(',').filter { it.isNotEmpty() }) {
                val permitted=schema.optJSONObject("allowed")?.optJSONObject(frame)?.optJSONArray(key) ?: schema.getJSONObject("slots").getJSONArray(key)
                fields.put(key,JSONArray().also { out->permitted.strings().forEach { out.put(JSONArray("[$it]").get(0)) } })
            }
            if(!variants.has(action))variants.put(action,JSONArray())
            variants.getJSONArray(action).put(fields)
        }
        variants.put("CALL_CONTACT",JSONArray().put(JSONObject().put("contact_query",JSONObject.NULL)))
        catalog=JSONObject().put("titles",titles).put("schema",variants)
        val bytes=assets.read("model.onnx")
        try {
            check(MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}==version)
            OrtSession.SessionOptions().use { options->
                options.setIntraOpNumThreads(2);options.setInterOpNumThreads(1)
                session=OrtEnvironment.getEnvironment().createSession(bytes,options)
            }
        } finally {bytes.fill(0)}
        predict("سلام")
    }
    fun validate(action:String,params:JSONObject):Boolean {
        if(action=="CALL_CONTACT")return params.length()==1 && params.opt("contact_query") is String && params.getString("contact_query").trim().length in 1..80
        val variants=catalog.getJSONObject("schema").optJSONArray(action)?:return false
        return (0 until variants.length()).any { i->
            val fields=variants.getJSONObject(i)
            fields.keys().asSequence().toSet()==params.keys().asSequence().toSet() && fields.keys().asSequence().all { k->
                val values=fields.getJSONArray(k)
                (0 until values.length()).any { j->val a=values.get(j);val b=params.get(k);a==b || a is Number && b is Number && a.toDouble()==b.toDouble() }
            }
        }
    }
    fun prediction(text:String,action:String,params:JSONObject,score:Double,personal:Boolean=false)=JSONObject()
        .put("text",text).put("prediction",JSONObject().put("label",action).put("title",catalog.getJSONObject("titles").getString(action)).put("score",score))
        .put("slots",params).put("model",version).put("personal",personal)
    fun predict(raw:String):JSONObject {
        val text=RahaWordPiece.normalize(raw)
        val ids=tokenizer.encode(text).ids.take(48).toLongArray()
        val shape=longArrayOf(1,ids.size.toLong());val env=OrtEnvironment.getEnvironment()
        OnnxTensor.createTensor(env,LongBuffer.wrap(ids),shape).use { input->
            OnnxTensor.createTensor(env,LongBuffer.wrap(LongArray(ids.size){1}),shape).use { mask->
                OnnxTensor.createTensor(env,LongBuffer.wrap(LongArray(ids.size)),shape).use { types->
                    session.run(mapOf("input_ids" to input,"attention_mask" to mask,"token_type_ids" to types)).use { output->
                        fun probabilities(name:String):List<Double>{
                            val b=(output.get(name).get() as OnnxTensor).floatBuffer
                            val logits=FloatArray(b.remaining());b.get(logits);val max=logits.max()
                            val p=logits.map { exp((it-max).toDouble()) };return p.map { it/p.sum() }
                        }
                        val scores=probabilities("frames");val winner=scores.indices.maxBy { scores[it] }
                        val frame=frames[winner];val action=frame.substringBefore('|');val params=linkedMapOf<String,Any>();val allowed=mutableMapOf<String,List<Any>>()
                        for(key in frame.substringAfter('|').split(',').filter { it.isNotEmpty() }) {
                            val serialized=schema.getJSONObject("slots").getJSONArray(key).strings()
                            val permitted=schema.optJSONObject("allowed")?.optJSONObject(frame)?.optJSONArray(key)?.strings()?:serialized
                            val p=probabilities("slot_$key");val best=permitted.maxBy { p[serialized.indexOf(it)] }
                            params[key]=JSONArray("[$best]").get(0);allowed[key]=permitted.map { JSONArray("[$it]").get(0) }
                        }
                        val grounded=RahaIntentGrounding.apply(text,action,params,allowed).parameters.toMutableMap()
                        if(action=="CALL_CONTACT"){grounded.clear();grounded["contact_query"]=RahaContactSpan.extract(text,contacts).orEmpty()}
                        return prediction(text,action,JSONObject(grounded),round(scores[winner]*10000)/10000)
                    }
                }
            }
        }
    }
    override fun close(){if(::session.isInitialized)session.close()}
    private fun JSONArray.strings()=(0 until length()).map(::getString)
}
