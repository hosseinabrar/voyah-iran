package com.abrar.voyahvoicetest

import android.content.Context
import android.util.AtomicFile
import com.abrar.raha.intent.RahaWordPiece
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Durable local corrections, export compatible with import_reviewed.py. Never auto-delete on share. */
class TrainingStore(private val context:Context) {
    private val file=AtomicFile(File(context.filesDir,"tester-training.json"))
    private val preferences=context.getSharedPreferences("tester",Context.MODE_PRIVATE)
    val device:String=preferences.getString("device",null)?:UUID.randomUUID().toString().replace("-","").also { preferences.edit().putString("device",it).commit() }
    var name:String
        get()=preferences.getString("name","").orEmpty()
        set(value){require(value.trim().length in 1..60);check(preferences.edit().putString("name",value.trim()).commit())}
    private fun read():JSONArray=if(file.baseFile.exists()||File(file.baseFile.path+".bak").exists())file.openRead().bufferedReader().use { JSONArray(it.readText()) } else JSONArray()
    fun override(text:String,model:String):JSONObject? {
        val rows=read();val normalized=RahaWordPiece.normalize(text)
        for(i in rows.length()-1 downTo 0){val row=rows.getJSONObject(i);if(row.getString("text")==normalized&&row.getString("model")==model)return row}
        return null
    }
    fun add(result:JSONObject,action:String,params:JSONObject,kind:String) {
        require(name.isNotEmpty());require(kind in setOf("correct","confirm"))
        val row=JSONObject().put("id",UUID.randomUUID().toString().replace("-","")).put("device",device).put("tester",name)
            .put("text",RahaWordPiece.normalize(result.getString("text"))).put("action",action).put("parameters",params).put("kind",kind)
            .put("model",result.getString("model")).put("prediction",result).put("time",System.currentTimeMillis()/1000.0).put("review","pending")
        val rows=read().put(row);val stream=file.startWrite()
        try{stream.write(rows.toString().toByteArray(Charsets.UTF_8));file.finishWrite(stream)}catch(e:Exception){file.failWrite(stream);throw e}
    }
    fun export():File {
        val rows=read();check(rows.length()>0){"هنوز آموزشی ثبت نشده است"}
        val folder=File(context.cacheDir,"feedback");check(folder.isDirectory||folder.mkdirs())
        // Unique immutable snapshots keep an earlier share valid while new feedback arrives.
        return File(folder,"raha-training-${device.take(8)}-${System.currentTimeMillis()}.jsonl").also { target->
            target.bufferedWriter().use { out->for(i in 0 until rows.length()){out.write(rows.getJSONObject(i).toString());out.newLine()} }
        }
    }
}
