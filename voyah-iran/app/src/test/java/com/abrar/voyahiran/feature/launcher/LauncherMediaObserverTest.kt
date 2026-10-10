package com.abrar.voyahiran.feature.launcher
import android.app.Application
import android.media.session.MediaController
import android.media.session.MediaSession
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[30],application=Application::class)
class LauncherMediaObserverTest {
 @Test fun repeatedSnapshotsKeepCallbackAndRemoveOnlyDepartedSessions() {
  val ctx=RuntimeEnvironment.getApplication()
  val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
  val session=MediaSession(ctx,"player")
  val observer=LauncherMediaObserver(ctx,scope){}
  val bind=observer.javaClass.getDeclaredMethod("bind",List::class.java).apply {isAccessible=true}
  val field=observer.javaClass.getDeclaredField("controllers").apply {isAccessible=true}
  try {
   bind.invoke(observer,listOf(session.controller))
   val initial=(field.get(observer) as Map<*,*>).values.single()
   bind.invoke(observer,listOf(MediaController(ctx,session.sessionToken)))
   assertSame(initial,(field.get(observer) as Map<*,*>).values.single())
   bind.invoke(observer,emptyList<MediaController>())
   assertTrue((field.get(observer) as Map<*,*>).isEmpty())
  } finally {observer.stop();scope.cancel();session.release()}
 }
}
