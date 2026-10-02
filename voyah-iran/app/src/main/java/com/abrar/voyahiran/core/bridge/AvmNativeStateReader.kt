package com.abrar.voyahiran.core.bridge

import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import com.abrar.voyahiran.core.logging.AppLog
import java.net.SocketAddress

/** Read-only multicast subscriber, matching dls_reverse_svr's socket(AF_NETLINK, SOCK_DGRAM, 23).
 * No sends, ioctl, logcat, hooks, or access to the OEM process's memory.
 * OEM arm64 receiver: 0x25a78 binds protocol 23/group 1; 0x25b38 polls/receives
 * raw 10-byte datagrams; 0x4bc20 decodes payload byte 1 & 3 as the view mode.
 */
internal class AvmNativeStateReader : AutoCloseable {
    data class Sample(val frame: AvmNativeFrame, val at: Long, val sequence: Long)
    @Volatile var sample: Sample? = null
        private set
    @Volatile private var stopped = false
    @Volatile var failed = false
        private set
    private val fd = Os.socket(OsConstants.AF_NETLINK, OsConstants.SOCK_DGRAM, 23)
    private val worker: Thread
    init {
        try {
            // Port 0 lets the kernel allocate an unused port; group 1 is the OEM multicast group.
            val address = Class.forName("android.system.NetlinkSocketAddress")
                .getConstructor(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                .newInstance(0, 1) as SocketAddress
            Os.setsockoptInt(fd, OsConstants.SOL_SOCKET, OsConstants.SO_RCVBUF, 65536)
            Os.bind(fd, address)
            worker = Thread({
                try {
                    val poll = StructPollfd().apply { this.fd = this@AvmNativeStateReader.fd; events = OsConstants.POLLIN.toShort() }
                    val buffer = ByteArray(64)
                    var sequence = 0L
                    while (!stopped) {
                        if (Os.poll(arrayOf(poll), 200) <= 0) continue
                        check((poll.revents.toInt() and (OsConstants.POLLERR or OsConstants.POLLHUP or OsConstants.POLLNVAL)) == 0) { "AVM channel closed" }
                        if ((poll.revents.toInt() and OsConstants.POLLIN) == 0) continue
                        val count = Os.read(fd, buffer, 0, buffer.size)
                        val frame = AvmNativeFrame.decode(buffer, count) ?: continue
                        val previous = sample?.frame
                        sample = Sample(frame, SystemClock.elapsedRealtime(), ++sequence)
                        if (frame != previous) AppLog.info("AvmTurn", "native AVM active=${frame.active} twoD=${frame.twoDimensional} view=${frame.view}")
                    }
                } catch (e: Exception) {
                    failed = true
                    sample = null
                    if (!stopped) AppLog.error("AvmTurn", "Native AVM receiver stopped", e)
                }
            }, "VoyahAvmState").apply { isDaemon = true; start() }
            AppLog.info("AvmTurn", "native read-only receiver bound protocol=23 group=1")
        } catch (e: Exception) { Os.close(fd); throw e }
    }
    override fun close() {
        stopped = true
        worker.join(500) // poll exits within 200 ms; no close/reuse race with its read.
        Os.close(fd)
        sample = null
    }
}
