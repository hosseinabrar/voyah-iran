package com.abrar.voyahiran.core.bridge

import com.abrar.voyahiran.core.shell.CommandResult
import com.abrar.voyahiran.core.shell.ProcessCommandRunner
import java.io.File
import com.abrar.voyahiran.feature.navigation.NeshanConfig

/** Theme preference contract comes exclusively from the verified APK profile. */
internal object NeshanThemeBridge {
    fun execute():CommandResult = try {
        check(BridgeSubscriptionSession.vipActive) {"VIP required"}
        val runner=ProcessCommandRunner(useBridge=false)
        val paths=runner.run(listOf("/system/bin/pm","path","--user","0",NeshanConfig.PACKAGE),5)
        check(paths.exitCode==0 && !paths.timedOut) {"Cannot resolve Neshan APK"}
        val apk=paths.output.lineSequence().map {it.trim().removePrefix("package:")}.single {it.endsWith("/base.apk")}
        check(apk.startsWith("/data/app/") || apk.startsWith("/system/"))
        val catalogs=listOf(File("/data/local/tmp/voyahiran-bridge/neshan",NeshanConfig.FILE_NAME),
            File("/data/user/0/com.abrar.voyahiran/files",NeshanConfig.FILE_NAME))
            .filter {it.isFile}.mapNotNull {runCatching {NeshanConfig.verify(NeshanConfig.read(it))}.getOrNull()}
        val catalog=catalogs.maxByOrNull {it.revision} ?: error("Verified Neshan config unavailable")
        val hash=NeshanConfig.fileHash(File(apk))
        val theme=catalog.profiles.singleOrNull {it.apkSha256==hash}?.json?.optJSONObject("androidTheme")
            ?: error("Android theme unsupported by this Neshan profile")
        val key=theme.getString("key");val value=theme.getInt("value")
        val directory=File("/data/user/0/"+NeshanConfig.PACKAGE+"/shared_prefs")
        check(directory.parentFile!!.isDirectory) {"Neshan data unavailable; open Neshan once"}
        val target=File(directory,theme.getString("preferences")+".xml")
        fun read()=if(target.exists())target.readText() else "<?xml version='1.0' encoding='utf-8'?><map/>"
        if(NeshanThemeXml.isAndroid(read(),key,value)) CommandResult(0,"NESHAN_THEME_ANDROID unchanged") else {
            val stopped=runner.run(listOf("/system/bin/am","force-stop","--user","0",NeshanConfig.PACKAGE),5)
            check(stopped.exitCode==0 && !stopped.timedOut) {"Cannot stop Neshan before updating theme"}
            val owner=android.system.Os.stat(directory.parentFile!!.path)
            if(!directory.exists()) {
                check(directory.mkdir());android.system.Os.chown(directory.path,owner.st_uid,owner.st_gid)
                android.system.Os.chmod(directory.path,448)
            }
            val updated=NeshanThemeXml.android(read(),key,value)
            val atomic=android.util.AtomicFile(target)
            val stream=atomic.startWrite()
            try {
                stream.write(updated.toByteArray(Charsets.UTF_8))
                android.system.Os.fchown(stream.fd,owner.st_uid,owner.st_gid)
                android.system.Os.fchmod(stream.fd,384)
                atomic.finishWrite(stream)
            } catch(e:Exception) {atomic.failWrite(stream);throw e}
            val labels=runner.run(listOf("/system/bin/restorecon","-RF",directory.path),5)
            check(labels.exitCode==0 && !labels.timedOut) {"Cannot restore Neshan preference label"}
            check(NeshanThemeXml.isAndroid(target.readText(),key,value))
            CommandResult(0,"NESHAN_THEME_ANDROID applied")
        }
    } catch(e:Exception) {CommandResult(1,"Neshan theme: ${e.message}")}
}

internal object NeshanThemeXml {
    private fun document(xml:String):org.w3c.dom.Document {
        val factory=javax.xml.parsers.DocumentBuilderFactory.newInstance()
        // Android's parser does not implement Xerces-specific security feature names.
        check(!xml.contains("<!DOCTYPE",ignoreCase=true) && !xml.contains("<!ENTITY",ignoreCase=true)) {"Unexpected preference document"}
        return factory.newDocumentBuilder().parse(org.xml.sax.InputSource(java.io.StringReader(xml))).also {check(it.documentElement.tagName=="map")}
    }
    fun isAndroid(xml:String,key:String,value:Int):Boolean {
        val nodes=document(xml).getElementsByTagName("int")
        return (0 until nodes.length).any { val node=nodes.item(it) as org.w3c.dom.Element;node.getAttribute("name")==key && node.getAttribute("value")==value.toString() }
    }
    fun android(xml:String,key:String,value:Int):String {
        val doc=document(xml);val root=doc.documentElement
        val nodes=root.childNodes
        for(i in nodes.length-1 downTo 0) {
            val node=nodes.item(i) as? org.w3c.dom.Element ?: continue
            if(node.getAttribute("name")==key)root.removeChild(node)
        }
        root.appendChild(doc.createElement("int").apply {setAttribute("name",key);setAttribute("value",value.toString())})
        val result=java.io.StringWriter()
        javax.xml.transform.TransformerFactory.newInstance().newTransformer().transform(javax.xml.transform.dom.DOMSource(doc),javax.xml.transform.stream.StreamResult(result))
        return result.toString()
    }
}
