package com.abrar.voyahiran.feature.permissions

import android.content.Context
import android.content.pm.PackageManager
import com.abrar.voyahiran.core.shell.ProcessCommandRunner
import com.abrar.voyahiran.core.logging.AppLog

data class InstalledApp(val packageName: String, val label: String) { override fun toString() = label }
data class AppPermission(val name: String, val label: String, val granted: Boolean, val available: Boolean = true)
class PermissionRepository(context: Context) {
    private val ownPackage = context.packageName
    private val pm = context.packageManager
    private val runner = ProcessCommandRunner()
    fun apps(): List<InstalledApp> = pm.getInstalledApplications(0).filter {
        it.packageName != ownPackage && (it.flags and (android.content.pm.ApplicationInfo.FLAG_SYSTEM or android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) == 0
    }.map { InstalledApp(it.packageName, it.loadLabel(pm).toString()) }.sortedBy { it.label.lowercase() }
    fun uninstall(pkg: String) {
        require(apps().any { it.packageName == pkg }) { "Not a removable user application" }
        AppLog.info("Permissions", "uninstall requested package=$pkg")
        val output = execute(listOf("pm", "uninstall", "--user", "0", pkg))
        check(output.lineSequence().any { it.trim() == "Success" }) { "Uninstall failed: $output" }
        check(apps().none { it.packageName == pkg }) { "Uninstall verification failed" }
        AppLog.info("Permissions", "uninstall verified package=$pkg")
    }
    private fun members(pkg: String): Map<String, List<String>> {
        val declared = pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().toSet()
        return linkedMapOf(
            "microphone" to listOf("android.permission.RECORD_AUDIO").filter { it in declared },
            "camera" to listOf("android.permission.CAMERA").filter { it in declared },
            "location" to listOf("android.permission.ACCESS_COARSE_LOCATION", "android.permission.ACCESS_FINE_LOCATION").filter { it in declared },
            "install" to listOf(INSTALL).filter { it in declared },
            "background" to listOf("RUN_ANY_IN_BACKGROUND", "RUN_IN_BACKGROUND", "idle"))
    }
    fun permissions(pkg: String): List<AppPermission> = members(pkg).map { (key, atoms) ->
        AppPermission(key, key, atoms.isNotEmpty() && atoms.all { enabled(it, read(pkg, it)) }, atoms.isNotEmpty())
    }
    private fun execute(args: List<String>): String {
        val result = runner.run(listOf("su", "0") + args, 15)
        check(!result.timedOut && result.exitCode == 0 && !Regex("Exception|Error|denied", RegexOption.IGNORE_CASE).containsMatchIn(result.output)) { "Permission command failed: ${result.output}" }
        return result.output
    }
    private fun op(atom: String) = if (atom == INSTALL) "REQUEST_INSTALL_PACKAGES" else atom
    private fun read(pkg: String, atom: String): String {
        if (atom == "idle") return execute(listOf("dumpsys", "deviceidle", "whitelist", "=$pkg")).trim().also { check(it in listOf("true", "false")) }
        if (atom.startsWith("android.permission.") && atom != INSTALL) return (pm.checkPermission(atom, pkg) == PackageManager.PERMISSION_GRANTED).toString()
        val operation = op(atom)
        val output = execute(listOf("cmd", "appops", "get", "--user", "0", pkg, operation))
        if (output.contains("No operations")) return "default"
        return Regex("$operation: (allow|deny|ignore|default|foreground)").find(output)?.groupValues?.get(1)
            ?: error("Unknown app-op state: $output")
    }
    private fun enabled(atom: String, value: String) = value == "true" || value == "allow" || (atom.startsWith("RUN_") && value == "default")
    private fun write(pkg: String, atom: String, value: String) {
        execute(when {
            atom == "idle" -> listOf("dumpsys", "deviceidle", "whitelist", (if (value == "true") "+" else "-") + pkg)
            atom.startsWith("android.permission.") && atom != INSTALL -> listOf("pm", if (value == "true") "grant" else "revoke", "--user", "0", pkg, atom)
            else -> listOf("cmd", "appops", "set", "--user", "0", pkg, op(atom), value)
        })
        check(read(pkg, atom) == value) { "Verification failed: $atom" }
    }
    fun set(pkg: String, permission: String, enabled: Boolean) {
        val groups = members(pkg)
        val atoms = if (permission == "all") groups.values.flatten() else groups[permission].orEmpty()
        require(atoms.isNotEmpty())
        val previous = atoms.associateWith { read(pkg, it) }
        val attempted = mutableListOf<String>()
        try {
            for (atom in if (enabled) atoms else atoms.reversed()) {
                attempted += atom
                val value = if (atom == "idle" || (atom.startsWith("android.permission.") && atom != INSTALL)) enabled.toString() else if (enabled) "allow" else "ignore"
                AppLog.info("Permissions", "package=$pkg atom=$atom value=$value")
                write(pkg, atom, value)
            }
        } catch (e: Exception) {
            for (atom in attempted.asReversed()) try { write(pkg, atom, previous.getValue(atom)) }
            catch (restore: Exception) { AppLog.error("Permissions", "rollback failed package=$pkg atom=$atom", restore) }
            throw e
        }
    }
    companion object { const val INSTALL = "android.permission.REQUEST_INSTALL_PACKAGES" }
}
