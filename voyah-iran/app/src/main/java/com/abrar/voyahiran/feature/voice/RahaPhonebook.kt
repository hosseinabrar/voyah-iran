package com.abrar.voyahiran.feature.voice

import android.content.Context
import com.abrar.raha.RahaContacts
import com.abrar.voyahiran.VoyahIranApplication
import com.abrar.voyahiran.core.shell.ProcessCommandRunner
import com.abrar.voyahiran.core.logging.AppLog
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

data class RahaContact(val id: String, val device: String, val original: String, val spoken: String, val number: String, val available: Boolean, val builtIn: Boolean = false)

/** App-private names; keep edits, partition by phone, and exclude disappeared numbers from dialing. */
object RahaPhonebook {
    private val scope = CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val mutex = Mutex()
    @Volatile private var started=false
    @Volatile var device=""; private set
    @Volatile private var records: List<RahaContact> = emptyList()
    @Volatile var status="مخاطب‌ها هنوز بارگذاری نشده‌اند"; private set
    fun contacts():List<RahaContact> {
        val defaults=com.abrar.raha.RahaDefaultContacts.entries.map {
            RahaContact("default-${it.number}",device,it.name,it.name,it.number,true,true)
        }
        val personal=RahaContacts.firstNumberPerContact(records.filter { it.device==device && it.available }) { it.original }
            .filter { com.abrar.raha.RahaDefaultContacts.find(it.spoken)==null }
        return defaults+personal
    }
    @Synchronized fun start(context: Context) {
        if(started) return
        started=true
        val app=context.applicationContext
        scope.launch {
            delay(10_000)
            var nextSync=0L
            while(isActive) {
                try {
                    if(VoicePreferences(app).callsEnabled && (app as VoyahIranApplication).subscriptions.vipAllowed) {
                        val current=readDevice()
                        val now=android.os.SystemClock.elapsedRealtime()
                        if(current!=device || now>=nextSync) {
                            AppLog.info("RahaContacts","automatic synchronization started")
                            val success=refresh(app)
                            val populated=records.any { it.device==device && it.available }
                            // An empty OEM book during boot is not a completed synchronization.
                            // Keep retrying, and also discover later edits on an unchanged phone.
                            nextSync=android.os.SystemClock.elapsedRealtime()+PhonebookSyncPolicy.delay(success,populated)
                        }
                    } else { RahaContacts.update(emptyList()); device=""; nextSync=0L }
                } catch(e:CancellationException) { throw e }
                catch(e:Exception) {
                    device=""; sync(app); nextSync=0L
                    AppLog.info("RahaContacts","automatic synchronization waiting type=${e.javaClass.simpleName}")
                }
                delay(30_000)
            }
        }
    }
    fun sync(context: Context) { RahaContacts.update(if(VoicePreferences(context).callsEnabled) contacts().map { it.spoken } else emptyList()) }
    private fun request(vararg parts:String): JSONObject {
        // Reads may retry a busy bridge; a dial operation is deliberately never retried.
        var response=ProcessCommandRunner().run(listOf("su","0","raha-phone")+parts, 8)
        repeat(3) { attempt ->
            if(response.exitCode==75) {
                Thread.sleep(150L*(attempt+1))
                response=ProcessCommandRunner().run(listOf("su","0","raha-phone")+parts,8)
            }
        }
        check(response.exitCode==0 && !response.timedOut) { "گوشی به بلوتوث متصل نیست یا مخاطب‌های برنامه فون هنوز آماده نیستند" }
        return JSONObject(response.output)
    }
    fun readDevice(): String = request("device").getString("device")
    suspend fun refresh(context: Context): Boolean = withContext(Dispatchers.IO) { mutex.withLock {
        var stage="device"
        try {
            check(VoicePreferences(context).callsEnabled && (context.applicationContext as VoyahIranApplication).subscriptions.vipAllowed)
            val mac=readDevice()
            val prefs=context.getSharedPreferences("raha_contacts",Context.MODE_PRIVATE)
            val saved=decode(prefs.getString("records","[]").orEmpty())
            stage="read-pages"
            val incoming=mutableListOf<Pair<String,String>>()
            var after=0L
            var done=false
            repeat(1000) {
                if(!done) {
                    ensureActive()
                    val page=request("read",mac,after.toString())
                    check(page.getString("device")==mac)
                    val rows=page.getJSONArray("contacts")
                    for(i in 0 until rows.length()) rows.getJSONObject(i).let { incoming += it.getString("name") to it.getString("number") }
                    val next=page.getLong("after")
                    done=page.getBoolean("done")
                    check(done || next>after)
                    after=next
                }
            }
            check(done && readDevice()==mac && VoicePreferences(context).callsEnabled)
            AppLog.info("RahaContacts","phonebook read rows=${incoming.size}")
            stage="convert-names"
            val byId=saved.associateBy { it.id }
            var converter: ContactNames?=null
            val imported=RahaContacts.firstNumberPerContact(incoming) { it.first }.map { (name,number) ->
                val id=MessageDigest.getInstance("SHA-256").digest("$mac\n$number\n$name".toByteArray()).joinToString("") { "%02x".format(it) }
                val old=byId[id]
                val spoken=old?.spoken ?: if(name.none { it in 'a'..'z' || it in 'A'..'Z' }) RahaContacts.normalize(name)
                    else (converter ?: ContactNames(context).also { converter=it }).convert(name)
                RahaContact(id,mac,name,spoken,number,true)
            }
            val ids=imported.map { it.id }.toSet()
            records=saved.filter { it.id !in ids }.map { if(it.device==mac) it.copy(available=false) else it }+imported
            stage="save"
            persist(context)
            device=mac
            sync(context)
            status=if(imported.isEmpty()) "دفترچه تلفن خالی است؛ همگام‌سازی مخاطب‌ها در برنامه فون را بررسی کنید" else "${imported.size} مخاطب بارگذاری شد"
            AppLog.info("RahaContacts","phonebook loaded count=${imported.size}")
            true
        } catch(e: CancellationException) { throw e }
        catch(e: Exception) {
            device=""; sync(context)
            status="بارگذاری مخاطب‌ها ناموفق بود؛ اتصال بلوتوث و همگام‌سازی مخاطب‌ها در برنامه فون را بررسی کنید"
            val frames=e.stackTrace.take(5).joinToString(" <- ") { "${it.className}.${it.methodName}:${it.lineNumber}" }
            AppLog.info("RahaContacts","phonebook unavailable stage=$stage type=${e.javaClass.simpleName} frames=$frames")
            false
        }
    } }
    suspend fun edit(context: Context, changes: Map<String,String>) = withContext(Dispatchers.IO) { mutex.withLock {
        require(changes.values.all { it.isNotBlank() && it.length<=120 && it.none(Char::isISOControl) })
        records=records.map { row -> changes[row.id]?.let { row.copy(spoken=RahaContacts.normalize(it)) } ?: row }
        persist(context); sync(context)
    } }
    private fun persist(context:Context) {
        val array=JSONArray()
        records.forEach { array.put(JSONObject().put("id",it.id).put("device",it.device).put("original",it.original).put("spoken",it.spoken).put("number",it.number).put("available",it.available)) }
        check(context.getSharedPreferences("raha_contacts",Context.MODE_PRIVATE).edit().putString("records",array.toString()).commit())
    }
    private fun decode(json:String):List<RahaContact> {
        val a=JSONArray(json)
        return (0 until a.length()).map { i -> a.getJSONObject(i).let { RahaContact(it.getString("id"),it.getString("device"),it.getString("original"),it.getString("spoken"),it.getString("number"),it.optBoolean("available")) } }
    }
    suspend fun dial(context:Context, contact:RahaContact, cancelled:()->Boolean): Boolean = withContext(Dispatchers.IO) {
        if(cancelled() || !VoicePreferences(context).enabled || !VoicePreferences(context).callsEnabled || !(context.applicationContext as VoyahIranApplication).subscriptions.vipAllowed ||
            contacts().none { it.id==contact.id && it.number==contact.number }) return@withContext false
        val targetDevice=if(contact.builtIn) runCatching { readDevice() }.getOrNull() ?: return@withContext false else contact.device
        if(cancelled()) return@withContext false
        val result=ProcessCommandRunner().run(listOf("su","0","raha-phone","dial",targetDevice,contact.number),8)
        result.exitCode==0 && !result.timedOut && result.output.trim()=="CALL_DISPATCHED"
    }
}
