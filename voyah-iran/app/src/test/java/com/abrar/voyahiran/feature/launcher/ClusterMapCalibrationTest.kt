package com.abrar.voyahiran.feature.launcher

import org.junit.Assert.*
import org.junit.Test

class ClusterMapCalibrationTest {
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
