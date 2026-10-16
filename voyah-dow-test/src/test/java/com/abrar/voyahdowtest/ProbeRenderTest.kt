package com.abrar.voyahdowtest
import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class, qualifiers = "en-w1280dp-h720dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProbeRenderTest {
    @Test fun renderPeakAndIdleFromActualView() {
        val app = RuntimeEnvironment.getApplication()
        app.setTheme(android.R.style.Theme_Material_NoActionBar)
        val view = ProbeView(app)
        view.status.text = "DOW_STATE: 3     Switch: 1     ACC: 2     Target: 3"
        view.logs.text = "PREVIEW / simulated values — no vehicle connected\nEVENT DOW_STATE=3\nSAMPLE state=3 switch=1 acc=2 target=3 match=true"
        view.measure(View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY))
        view.layout(0,0,1280,720)
        assertTrue(view.warning.height > 200)
        for ((name, alpha) in listOf("active" to 1f, "idle" to .12f)) {
            if (name == "idle") {
                view.status.text = "DOW_STATE: 1     Switch: 1     ACC: 2     Target: 3"
                view.logs.text = "PREVIEW / simulated values — no vehicle connected\nEVENT DOW_STATE=1\nSAMPLE state=1 switch=1 acc=2 target=3 match=false"
            }
            view.warning.alpha = alpha
            val bitmap = Bitmap.createBitmap(1280,720,Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val out = File("/Users/hosseinabrar/voyah/assets/dow-probe", "probe-" + name + ".png")
            out.parentFile!!.mkdirs()
            out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        }
    }
}
