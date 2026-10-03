package com.abrar.voyahiran.core.bridge

import java.io.File
import java.util.zip.ZipFile

/** qg_hal's verified transport: open /dev/cis_can, ioctl(fd, 1, ten-byte packet).
 * Use public libc rather than loading the vendor HAL and its private dependencies.
 */
internal class AvmNativeCommandPort : AutoCloseable {
    private val ioctl:com.sun.jna.Function
    private val descriptor:android.os.ParcelFileDescriptor
    private var closed=false
    init {
        check(android.os.Process.myUid()==0)
        // app_process has no application's native-library search directory.
        val abi=if(android.os.Process.is64Bit()) "arm64-v8a" else "armeabi-v7a"
        val directory=File("/data/local/tmp/voyahiran-bridge/avm-native").apply { check(isDirectory || mkdirs()) }
        val target=File(directory,"libjnidispatch.so")
        ZipFile("/data/local/tmp/voyahiran-bridge/bridge.apk").use { zip ->
            val entry=checkNotNull(zip.getEntry("lib/$abi/libjnidispatch.so"))
            val bytes=zip.getInputStream(entry).use { it.readBytes() }
            if(!target.isFile || !target.readBytes().contentEquals(bytes)) target.writeBytes(bytes)
        }
        System.setProperty("jna.boot.library.path",directory.path)
        System.setProperty("jna.nosys","true")
        ioctl=com.sun.jna.NativeLibrary.getInstance("c").getFunction("ioctl")
        val fd=android.system.Os.open("/dev/cis_can",android.system.OsConstants.O_RDWR or android.system.OsConstants.O_CLOEXEC,0)
        try { descriptor=android.os.ParcelFileDescriptor.dup(fd) }
        finally { android.system.Os.close(fd) }
        com.abrar.voyahiran.core.logging.AppLog.info("AvmTurn","native transport ready device=/dev/cis_can request=1")
    }
    @Synchronized fun send(packet:ByteArray):Boolean {
        check(!closed) { "AVM channel closed" }
        require(packet.size==10 && packet[0]==0x1c.toByte() && packet[1]==8.toByte())
        val memory=com.sun.jna.Memory(10)
        try {
            memory.write(0,packet,0,packet.size)
            val result=ioctl.invokeInt(arrayOf(descriptor.fd,com.sun.jna.NativeLong(1),memory))
            if(result<0) com.abrar.voyahiran.core.logging.AppLog.info("AvmTurn","native ioctl failed result=$result errno=${com.sun.jna.Native.getLastError()}")
            return result>=0
        } finally { memory.close() }
    }
    @Synchronized override fun close() { if(!closed) { closed=true; descriptor.close() } }
}
