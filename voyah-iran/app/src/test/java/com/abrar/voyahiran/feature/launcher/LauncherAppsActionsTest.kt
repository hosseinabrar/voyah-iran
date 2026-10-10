package com.abrar.voyahiran.feature.launcher
import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[30],application=android.app.Application::class)
class LauncherAppsActionsTest {
 private fun find(v:View,text:String):TextView? {
  if(v is TextView && v.text.toString()==text)return v
  if(v is ViewGroup)for(i in 0 until v.childCount)find(v.getChildAt(i),text)?.let {return it}
  return null
 }
 @Test fun removalRequiresExplicitConfirmInBothThemes() {
  for(light in listOf(false,true)) {
   val controller=Robolectric.buildActivity(Activity::class.java).setup()
   val host=FrameLayout(controller.get());controller.get().setContentView(host)
   val ui=LauncherAppActions(host,light);var removals=0
   ui.confirm("Music") {removals++}
   find(host,"Cancel")!!.performClick();assertEquals(0,removals);assertEquals(0,host.childCount)
   ui.confirm("Music") {removals++}
   host.getChildAt(0).performClick();assertEquals(0,removals)
   ui.confirm("Music") {removals++}
   find(host,"Remove")!!.performClick();assertEquals(1,removals);assertEquals(0,host.childCount)
   controller.destroy()
  }
 }
}
