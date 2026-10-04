package com.abrar.voyahiran.core.bridge

import android.os.Process
import com.abrar.voyahiran.core.logging.AppLog
import com.abrar.voyahiran.core.shell.CommandResult
import com.abrar.voyahiran.core.shell.ProcessCommandRunner
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Started by init using app_process, not an exported Android component. */
object BridgeServer {
    private val roa = RoaHookManager()
    private val neshan = NeshanHookManager()
    @JvmStatic fun main(args: Array<String>) {
        check(Process.myUid() == 0) { "Bridge must be started by the installer/init as root" }
        val token = File("/data/local/tmp/voyahiran-bridge/bridge.token").readText().trim()
        check(token.matches(Regex("[a-f0-9]{64}")))
        val runner = ProcessCommandRunner(useBridge = false)
        SystemLoggingControl.restoreAtBoot()
        // App stopping is suspended pending vehicle tests; boost controls system logging only.
        roa.start()
        neshan.start()
        OemNavigationManager.start()
        val execution = java.util.concurrent.locks.ReentrantLock()
        val pool = ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS, ArrayBlockingQueue<Runnable>(4))
        ServerSocket(BridgePolicy.PORT, 8, InetAddress.getByName("127.0.0.1")).use { server ->
            AppLog.info("BridgeServer", "ready protocol=1 uid=${Process.myUid()} port=${BridgePolicy.PORT}")
            while (true) {
                val socket = server.accept()
                try { pool.execute { handle(socket, token, runner, execution) } }
                catch (_: java.util.concurrent.RejectedExecutionException) { socket.close() }
            }
        }
    }

    private fun handle(socket: Socket, token: String, runner: ProcessCommandRunner, execution: java.util.concurrent.locks.ReentrantLock) {
        socket.use {
            try {
                socket.soTimeout = 3000
                val input = DataInputStream(socket.getInputStream())
                val output = DataOutputStream(socket.getOutputStream())
                val parsed = BridgeProtocol.read(input, token)
                val request = parsed.id
                val command = BridgePolicy.resolve(parsed.arguments, android.os.Build.VERSION.SDK_INT)
                val result = if (command == null) CommandResult(126, "Operation denied")
                // Display ACK polling must not hold up vehicle/startup commands.
                else if (command.first() == "app-display") AppDisplayBridge.set(command[1] == "enable")
                else if (command.first() == "app-density") AppDensityBridge.set(command[1])
                else if (command.first() == "app-density-package") AppDensityBridge.setPackage(command[1], command[2])
                else if (command.first() == "app-theme") AppThemeBridge.set(command[1])
                // A Back press must not wait behind long vehicle operations. Its DOWN/UP pair
                // has a separate lock and cannot change a task's display or global focus.
                else if (command == listOf("steering-back", "center")) LegacySteeringBack.press()
                else if (!execution.tryLock()) CommandResult(75, "Bridge busy; no command executed")
                else try {
                    AppLog.info("BridgeServer", "request=$request operation=${command.first()} accepted")
                    when (command.first()) {
                        "uninstall-user-app", "force-stop-user-app" -> {
                            val inventory = runner.run(listOf("/system/bin/pm", "list", "packages", "-3", "--user", "0"), 15)
                            if (inventory.exitCode != 0 || inventory.timedOut ||
                                inventory.output.lineSequence().none { it.trim() == "package:" + command[1] })
                                CommandResult(126, "Not a removable user application")
                            else if (command.first() == "force-stop-user-app")
                                runner.run(listOf("/system/bin/am", "force-stop", "--user", "0", command[1]), parsed.timeout)
                            else runner.run(listOf("/system/bin/pm", "uninstall", "--user", "0", command[1]), parsed.timeout)
                        }
                        "launcher-map", "launcher-map-claim", "launcher-map-status" -> LauncherMapBridge.execute(command)
                        "launcher-home" -> {
                            val enable=command[1]=="enable"
                            val access=if(enable && android.os.Build.VERSION.SDK_INT==28) runner.run(LauncherDrawerAccess.arguments,5) else CommandResult(0,"")
                            if(access.exitCode!=0 || access.timedOut) access else runner.run(listOf("/system/bin/settings","put","global","voyah_iran_launcher_home",if(enable)"1" else "0"),3)
                        }
                        "raha-phone" -> OemPhoneBridge.execute(command)
                        "local-profile-test" -> LocalProfileTest.execute(command[1])
                        "apollo-capability" -> ApolloCapability.read()
                        "avm-turn" -> AvmTurnBridge.execute(command[1])
                        "apollo-test-read" -> ApolloExperimentBridge.execute()
                        "apollo-test-set" -> ApolloExperimentBridge.execute(command[1], command[2].toInt())
                        "apollo-feature" -> ApolloBundle.setFeature(command[1], command[2] == "on")
                        "apollo-reconcile" -> ApolloBundle.reconcile(command[1].toInt())
                        "apollo-state" -> ApolloBundle.apply(command[1].toInt())
                        "system-logging" -> SystemLoggingControl.command(command[1])
                        "neshan-direct" -> neshan.command(command[1])
                        "cluster-navigation" -> OemClusterNavigation.command(command)
                        "subscription-vin" -> VehicleIdentity.read()
                        "subscription-session" -> BridgeSubscriptionSession.update(command[1].toInt())
                        "raha-command" -> RahaVehicleBridge.execute(command[1], command[2])
                        "raha-seat-ui" -> OemSeatPopup.show()
                        "screen-swap-runtime" -> OemScreenSwapManager.command(command[1])
                        "roa-hook" -> roa.command(command[1])
                        "roa-config" -> RearCameraConfiguration.enable()
                        else -> runner.run(command, parsed.timeout)
                    }
                } finally { execution.unlock() }
                output.writeInt(BridgePolicy.MAGIC)
                output.writeUTF(request)
                output.writeInt(result.exitCode)
                output.writeUTF(result.output.take(8000))
                output.writeBoolean(result.timedOut)
                output.flush()
            } catch (error: Exception) {
                AppLog.error("BridgeServer", "request-failed no-replay", error)
            }
        }
    }
}

