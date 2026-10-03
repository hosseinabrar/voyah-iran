package com.abrar.voyahiran.core.bridge

import android.database.sqlite.SQLiteDatabase
import com.abrar.voyahiran.core.shell.CommandResult
import com.abrar.voyahiran.core.shell.ProcessCommandRunner
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Read-only snapshot of the OEM phone's synced phonebook. No account or DB modifications. */
internal object OemPhoneBridge {
    private const val PACKAGE = "com.qinggan.bluetoothphone"
    private fun activePhone(): String = (Class.forName("android.os.SystemProperties")
        .getMethod("get", String::class.java).invoke(null, "qinggan.bluetooth.mac") as String)
        .trim().uppercase().also { check(it.matches(Regex("[0-9A-F]{2}(?::[0-9A-F]{2}){5}"))) { "PHONE_DISCONNECTED" } }
    fun execute(args: List<String>): CommandResult = try {
        val mac = activePhone()
        if (args[1] == "device") CommandResult(0, JSONObject().put("device", mac).toString())
        else if(args[1]=="dial" && args[3] in com.abrar.raha.RahaDefaultContacts.numbers) {
            check(mac==args[2]) { "PHONE_CHANGED" }
            dispatch(mac,args[3])
        } else {
            check(mac == args[2]) { "PHONE_CHANGED" }
            val file = listOf("/data/user/0/$PACKAGE/databases/contacts.db", "/data/data/$PACKAGE/databases/contacts.db")
                .map(::File).firstOrNull(File::isFile) ?: error("PHONEBOOK_UNAVAILABLE")
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                if (args[1] == "read") {
                    val page=readPage(db,mac,args[3].toLong())
                    check(activePhone() == mac) { "PHONE_CHANGED" }
                    CommandResult(0,page.toString())
                } else {
                    check(BridgeSubscriptionSession.active) { "SUBSCRIPTION_INACTIVE" }
                    val number = args[3]
                    // The caller cannot use this operation to dial arbitrary numbers or USSD.
                    var found = false
                    db.query("contactsinfo", arrayOf("number"), "upper(mac)=?", arrayOf(mac), null, null, null).use { c ->
                        while (c.moveToNext()) if (c.getString(0).orEmpty().replace(Regex("[\\s()\\-]"), "") == number) found = true
                    }
                    check(found && activePhone() == mac) { "CONTACT_UNAVAILABLE" }
                    dispatch(mac,number)
                }
            }
        }
    } catch (e: Exception) {
        com.abrar.voyahiran.core.logging.AppLog.info("RahaContacts", "OEM phone operation=${args.getOrNull(1)} failed type=${e.javaClass.simpleName}")
        CommandResult(1, "Phone unavailable, disconnected, changed, or phonebook not synchronized")
    }
    /** OEM schema contains only name/mac/number; it has no Android-style _id column. */
    internal fun readPage(db:SQLiteDatabase, mac:String, after:Long):JSONObject {
        require(after>=0)
        val rows=JSONArray()
        var last=after
        db.query("contactsinfo",arrayOf("rowid","name","number"),"upper(mac)=? AND rowid>?",
            arrayOf(mac,after.toString()),null,null,"rowid ASC","12").use { cursor ->
            while(cursor.moveToNext()) {
                last=cursor.getLong(0)
                val name=cursor.getString(1).orEmpty()
                val number=cursor.getString(2).orEmpty().replace(Regex("[\\s()\\-]"),"")
                if(name.length in 1..120 && number.matches(Regex("\\+?[0-9]{3,20}")))
                    rows.put(JSONObject().put("name",name).put("number",number))
            }
        }
        return JSONObject().put("device",mac).put("after",last).put("done",last==after).put("contacts",rows)
    }
    private fun dispatch(mac:String,number:String):CommandResult {
        check(BridgeSubscriptionSession.active) { "SUBSCRIPTION_INACTIVE" }
        check(activePhone()==mac) { "PHONE_CHANGED" }
        val result = ProcessCommandRunner(useBridge = false).run(listOf("/system/bin/am", "broadcast", "--user", "0",
            "-n", "$PACKAGE/$PACKAGE.logic.component.MakeCallBroadcastReceiver",
            "-a", "com.customs.broadcast.makecall", "--es", "makecall_phonenum", number), 5)
        return if (result.exitCode == 0 && !result.timedOut && result.output.contains("Broadcast completed"))
            CommandResult(0, "CALL_DISPATCHED") else CommandResult(1, "CALL_DISPATCH_FAILED")
    }

}
