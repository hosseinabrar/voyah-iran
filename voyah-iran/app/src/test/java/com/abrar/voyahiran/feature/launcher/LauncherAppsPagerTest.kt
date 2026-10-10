package com.abrar.voyahiran.feature.launcher

import android.app.Application
import android.view.View
import android.widget.FrameLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,30],application=Application::class)
class LauncherAppsPagerTest {
    @Test fun pagesStayInsideDrawerAndCanExceedTwo() {
        val context=RuntimeEnvironment.getApplication()
        val pager=LauncherPager(context)
        repeat(4){pager.addView(FrameLayout(context))}
        pager.measure(View.MeasureSpec.makeMeasureSpec(1775,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(640,View.MeasureSpec.EXACTLY))
        pager.layout(0,0,1775,640)
        pager.show(3,false)
        assertEquals(3,pager.page)
        assertEquals(0f,pager.getChildAt(3).translationX)
        pager.show(20,false);assertEquals(3,pager.page)
        pager.show(-1,false);assertEquals(0,pager.page)
        assertEquals(0f,pager.getChildAt(0).translationX)
    }
    @Test fun existingHomeStillHasTwoPages() {
        val context=RuntimeEnvironment.getApplication()
        val pager=LauncherPager(context)
        repeat(2){pager.addView(FrameLayout(context))}
        pager.show(4,false);assertEquals(1,pager.page)
        pager.show(-2,false);assertEquals(0,pager.page)
    }
}
