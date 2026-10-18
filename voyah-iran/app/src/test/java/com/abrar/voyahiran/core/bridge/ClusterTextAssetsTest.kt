package com.abrar.voyahiran.core.bridge

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.abrar.voyahiran.core.bridge.clustertext.ClusterTextAssets
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[30], application=Application::class, qualifiers="en-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ClusterTextAssetsTest {
    @Test fun localTextRendersWithoutAttachingToWindow() {
        val context=RuntimeEnvironment.getApplication()
        val root=LinearLayout(context).apply { orientation=LinearLayout.VERTICAL }
        val views=listOf(TextView(context), ClusterTextAssets.MarqueeBoldTextView(context,context),
            ClusterTextAssets.MarqueeTextView(context), TextView(context))
        views.zip(listOf("Apple Music","Tamasha","Shadmehr Aghili","Please open multimedia app")).forEachIndexed { i,(view,label) ->
            view.id=100+i; view.text=label; view.textSize=22f; view.setTextColor(Color.WHITE)
            root.addView(view,LinearLayout.LayoutParams(420,60))
        }
        assertEquals(4,ClusterTextAssets.install(root,context))
        assertTrue(root.getChildAt(1) is ClusterTextAssets.MarqueeBoldTextView)
        assertTrue(root.getChildAt(2) is ClusterTextAssets.MarqueeTextView)
        root.measure(View.MeasureSpec.makeMeasureSpec(420,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(240,View.MeasureSpec.EXACTLY))
        root.layout(0,0,420,240)
        val bitmap=Bitmap.createBitmap(420,240,Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        for(row in 0..3) {
            assertEquals(100+row,root.getChildAt(row).id)
            var visible=0
            for(y in row*60 until (row+1)*60) for(x in 0 until 420) if(Color.alpha(bitmap.getPixel(x,y))>0) visible++
            assertTrue("Text row $row must render in detached bitmap",visible>100)
        }
        bitmap.recycle()
    }
}
