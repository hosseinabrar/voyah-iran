package com.abrar.voyahiran.core.bridge

import com.abrar.voyahiran.core.shell.CommandResult
import com.abrar.voyahiran.core.shell.ProcessCommandRunner
import java.io.File

/** Neshan Setting3 / MapLightMode: AUTOMATIC=0, DAY=1, NIGHT=2, ANDROID_SETTING=3. */
internal object NeshanThemeBridge {
    fun execute():CommandResult = try {
        check(BridgeSubscriptionSession.vipActive) {"VIP required"}
        val directory=File("/data/user/0/org.rajman.neshan/shared_prefs")
        check(directory.parentFile!!.isDirectory) {"Neshan data unavailable; open Neshan once"}
        val target=File(directory,"Setting3.xml")
        fun read()=if(target.exists())target.readText() else "<?xml version='1.0' encoding='utf-8'?><map/>"
        if(NeshanThemeXml.isAndroid(read())) CommandResult(0,"NESHAN_THEME_ANDROID unchanged") else {
            val runner=ProcessCommandRunner(useBridge=false)
            val stopped=runner.run(listOf("/system/bin/am","force-stop","--user","0","org.rajman.neshan"),5)
            check(stopped.exitCode==0 && !stopped.timedOut) {"Cannot stop Neshan before updating theme"}
            val owner=android.system.Os.stat(directory.parentFile!!.path)
            if(!directory.exists()) {
                check(directory.mkdir());android.system.Os.chown(directory.path,owner.st_uid,owner.st_gid)
                android.system.Os.chmod(directory.path,448)
            }
            val updated=NeshanThemeXml.android(read())
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
            check(NeshanThemeXml.isAndroid(target.readText()))
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
    fun isAndroid(xml:String):Boolean {
        val nodes=document(xml).getElementsByTagName("int")
        return (0 until nodes.length).any { val node=nodes.item(it) as org.w3c.dom.Element;node.getAttribute("name")=="MapLightMode" && node.getAttribute("value")=="3" }
    }
    fun android(xml:String):String {
        val doc=document(xml);val root=doc.documentElement
        val nodes=root.childNodes
        for(i in nodes.length-1 downTo 0) {
            val node=nodes.item(i) as? org.w3c.dom.Element ?: continue
            if(node.getAttribute("name")=="MapLightMode")root.removeChild(node)
        }
        root.appendChild(doc.createElement("int").apply {setAttribute("name","MapLightMode");setAttribute("value","3")})
        val result=java.io.StringWriter()
        javax.xml.transform.TransformerFactory.newInstance().newTransformer().transform(javax.xml.transform.dom.DOMSource(doc),javax.xml.transform.stream.StreamResult(result))
        return result.toString()
    }
}
