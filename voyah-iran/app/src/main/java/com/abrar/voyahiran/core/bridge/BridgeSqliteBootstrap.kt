package com.abrar.voyahiran.core.bridge

/** SQLite in the root app_process must not acquire Settings providers via a synthetic Application. */
internal object BridgeSqliteBootstrap {
    private var prepared = false

    @Synchronized fun prepare() {
        if (prepared) return
        // Same defaults SQLite uses when no Application exists (the original phonebook bridge).
        // systemMain() used by other bridge services creates an Application without an AMS record;
        // SQLite's lazy global-settings lookup then throws SecurityException. Initialize just this
        // process's compatibility flags explicitly. Does not change system settings, DB mode or WAL.
        Class.forName("android.database.sqlite.SQLiteCompatibilityWalFlags")
            .getMethod("init", String::class.java).invoke(null, "")
        prepared = true
    }
}
