package com.abrar.voyahiran.feature.launcher

import org.junit.Assert.*
import org.junit.Test

class ClusterMapCalibrationTest {
    @Test fun vehiclePresetsMatchBothDrivingModes() {
        val expected=listOf(
            Triple(4,true,listOf(-229f,-31f,1f,-24f,0f,.99f)),
            Triple(4,false,listOf(-20f,-21f,1f,-24f,0f,.99f)),
            Triple(1,true,listOf(-200f,40f,1f,13f,80f,.6f)),
            Triple(1,false,listOf(7f,40f,1f,13f,80f,.6f)),
            Triple(2,true,listOf(-194f,-19f,1.04f,0f,0f,1f)),
            Triple(2,false,listOf(28f,-19f,1.02f,0f,0f,1f)))
        expected.forEach { (theme,route,values) ->
            val t=ClusterMapCalibration.select(theme,route)
            assertEquals(values,listOf(t.mapX,t.mapY,t.mapScale,t.maskX,t.maskY,t.maskScale))
            assertEquals(theme!=2,t.maskEnabled)
            assertEquals(theme==1,t.circleMask)
        }
    }
    @Test fun minimalistMatchesTestAppCircleBounds() {
        for (routing in listOf(false,true)) {
            val t=ClusterMapCalibration.select(1,routing)
            assertEquals(153f,t.maskLeft,.001f)
            assertEquals(230f,t.maskTop,.001f)
            assertEquals(420f,t.panelWidth*t.maskScale,.001f)
            assertEquals(70f,t.mapTop(660),.001f)
            assertEquals(if(routing) -285.5f else -78.5f,t.mapLeft(871),.001f)
        }
    }
    @Test fun usesMediaPanelRatherThanEntireDisplay() {
        val t=ClusterMapCalibration()
        assertEquals(90f,t.maskLeft,0f)
        assertEquals(154f,t.maskTop,0f)
        assertEquals(490f,t.panelWidth,0f)
        assertEquals(506f,t.panelHeight,0f)
        assertEquals(-100.5f,t.mapLeft(871),0f)
        assertEquals(77f,t.mapTop(660),0f)
    }
    @Test fun maskAndMapTransformsRemainIndependentAndScaleAboutTheirCenters() {
        val base=ClusterMapCalibration()
        val mask=base.copy(maskX=40f,maskY=-20f,maskScale=0.5f)
        assertEquals(base.mapLeft(871),mask.mapLeft(871),0f)
        assertEquals(base.mapTop(660),mask.mapTop(660),0f)
        assertEquals(base.centerX+40,mask.maskLeft+mask.panelWidth*mask.maskScale/2,0f)
        assertEquals(base.centerY-20,mask.maskTop+mask.panelHeight*mask.maskScale/2,0f)
        val map=mask.copy(mapX=-50f,mapY=15f,mapScale=2f)
        assertEquals(mask.maskLeft,map.maskLeft,0f)
        assertEquals(mask.maskTop,map.maskTop,0f)
        assertEquals(base.centerX-50,map.mapLeft(871)+871*map.mapScale/2,0f)
        assertEquals(base.centerY+15,map.mapTop(660)+660*map.mapScale/2,0f)
    }
}
