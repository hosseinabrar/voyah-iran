package com.abrar.voyahiran.feature.keyboard.domain

data class Keyboard(val id: String, val packageName: String)
data class KeyboardSnapshot(val current: String?, val keyboards: List<Keyboard>) {
    val gboard get() = keyboards.firstOrNull { it.packageName == GBOARD }
    val enabled get() = current?.substringBefore('/') == GBOARD
    companion object { const val GBOARD = "com.google.android.inputmethod.latin" }
}
enum class KeyboardFailure { NOT_INSTALLED, NO_RESTORE, FAILED }
class KeyboardException(val failure: KeyboardFailure) : Exception(failure.name)
interface KeyboardPort {
    fun read(): KeyboardSnapshot
    fun previous(): String?
    fun savePrevious(id: String)
    fun select(id: String)
}
class KeyboardRepository(private val port: KeyboardPort) {
    fun read() = port.read()
    fun setEnabled(enabled: Boolean): KeyboardSnapshot {
        val before = port.read()
        if (before.enabled == enabled) return before
        val target = if (enabled) {
            val gboard = before.gboard ?: throw KeyboardException(KeyboardFailure.NOT_INSTALLED)
            val old = before.keyboards.firstOrNull { it.id == before.current }
                ?: throw KeyboardException(KeyboardFailure.NO_RESTORE)
            port.savePrevious(old.id) // Commit before changing the actual input method.
            gboard.id
        } else {
            before.keyboards.firstOrNull { it.id == port.previous() && it.packageName != KeyboardSnapshot.GBOARD }?.id
                ?: before.keyboards.firstOrNull {
                    it.packageName == "com.qinggan.app.qgime" &&
                        (it.id.endsWith("/com.qinggan.app.qgime.QingganIME") || it.id.endsWith("/.QingganIME"))
                }?.id ?: throw KeyboardException(KeyboardFailure.NO_RESTORE)
        }
        port.select(target)
        return port.read().also { if (it.current != target) throw KeyboardException(KeyboardFailure.FAILED) }
    }
}
