package com.abrar.voyahiran.feature.voice
import android.app.Activity
import android.app.Application
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import com.abrar.raha.feedback.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[30],application=Application::class)
class RahaFeedbackDesignTest {
 @Test fun dialogueAndInterruptedExitKeepTheSameWindow() {
  val controller=Robolectric.buildActivity(Activity::class.java).setup()
  val a=controller.get();val host=FrameLayout(a);a.setContentView(host)
  val feedback=RahaFeedback(a,{},host=host)
  try {
   feedback.begin(manual=true)
   val window=host.getChildAt(0)
   feedback.prompt("کدام مخاطب؟",listOf("اول","دوم")){}
   assertSame(window,host.getChildAt(0));assertEquals(1,host.childCount)
   feedback.processing()
   feedback.prompt("تماس بگیرم؟",listOf("اول"),confirmation=true){}
   assertSame(window,host.getChildAt(0))
   feedback.dismiss();assertFalse(feedback.showing)
   feedback.begin(manual=true)
   shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400))
   assertTrue(feedback.showing);assertSame(window,host.getChildAt(0))
   feedback.close();assertEquals(0,host.childCount)
  } finally { feedback.close();controller.pause().stop().destroy() }
 }
 private fun children(v:View):List<View> = listOf(v)+(if(v is ViewGroup)(0 until v.childCount).flatMap { children(v.getChildAt(it)) } else emptyList())
 @Test fun confirmationKeepsRtlQuestionAndRoutesAnswersAndTheme() {
  val controller=Robolectric.buildActivity(Activity::class.java).setup()
  val a=controller.get();val host=FrameLayout(a);a.setContentView(host)
  var selected=-1;var cancelled=false;var light=false
  val feedback=RahaFeedback(a,{cancelled=true},host=host,lightTheme={light})
  try {
   feedback.prompt("با ناهید تماس بگیرم؟",listOf("ناهید"),confirmation=true){selected=it}
   repeat(3) { host.measure(View.MeasureSpec.makeMeasureSpec(1280,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.EXACTLY));host.layout(0,0,1280,720) }
   val buttons=children(host).filterIsInstance<Button>()
   assertEquals(listOf("خیر","بله"),buttons.map { it.text.toString() })
   val question=children(host).filterIsInstance<TextView>().first { it.text.toString().startsWith("با ناهید") }
   val row=question.parent as ViewGroup
   row.measure(View.MeasureSpec.makeMeasureSpec(680,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));row.layout(0,0,680,row.measuredHeight)
   assertTrue("question=${question.left}, answer=${buttons.last().right}",question.left>buttons.last().right)
   assertFalse(host.findViewById<RahaOrbView>(com.abrar.raha.feedback.R.id.orb).light)
   feedback.speech(true)
   shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(70))
   assertEquals(FeedbackState.LISTENING,host.findViewById<RahaOrbView>(com.abrar.raha.feedback.R.id.orb).phase)
   feedback.speech(false)
   shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(70))
   assertEquals(FeedbackState.WAITING,host.findViewById<RahaOrbView>(com.abrar.raha.feedback.R.id.orb).phase)
   light=true;shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1100))
   assertTrue(host.findViewById<RahaOrbView>(com.abrar.raha.feedback.R.id.orb).light)
   children(host).filterIsInstance<Button>().first { it.text=="بله" }.performClick();assertEquals(0,selected)
   feedback.processing()
   assertTrue(children(host).filterIsInstance<Button>().none { it.isEnabled })
   feedback.prompt("با ناهید تماس بگیرم؟",listOf("ناهید"),confirmation=true){selected=it}
   children(host).filterIsInstance<Button>().first { it.text=="خیر" }.performClick()
   assertTrue(cancelled);assertFalse(feedback.showing)
  } finally { feedback.close();controller.pause().stop().destroy() }
 }
}


