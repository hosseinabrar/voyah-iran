package com.abrar.voyahiran.core.bridge

import android.os.IBinder
import android.os.RemoteException
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,30],application=android.app.Application::class)
class BridgeSystemLivenessTest {
    @Test fun deathRestartsOnceAndRegistrationRaceIsCovered() {
        for (mode in listOf("live","already-dead","registration-death")) {
            var callback: IBinder.DeathRecipient? = null
            var restarts=0
            val binder=Proxy.newProxyInstance(IBinder::class.java.classLoader,arrayOf(IBinder::class.java)) { _,method,args ->
                when(method.name) {
                    "linkToDeath" -> {
                        callback=args!![0] as IBinder.DeathRecipient
                        if(mode=="registration-death")throw RemoteException("dead")
                        null
                    }
                    "isBinderAlive" -> mode=="live"
                    else -> error("Unexpected call ${method.name}")
                }
            } as IBinder
            BridgeSystemLiveness.watch(binder){restarts++}
            assertEquals(if(mode=="live")0 else 1,restarts)
            callback!!.binderDied()
            callback!!.binderDied()
            assertEquals(1,restarts)
        }
    }
}
