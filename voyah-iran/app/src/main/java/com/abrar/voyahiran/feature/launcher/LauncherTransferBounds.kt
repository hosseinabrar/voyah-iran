package com.abrar.voyahiran.feature.launcher

import android.graphics.Rect

/** Match the framework's acknowledged policy, using physical pixels rather than app density. */
internal object LauncherTransferBounds {
    fun resolve(width:Int,height:Int,request:String?,ack:String?,eligible:Boolean):Rect {
        val enabled=if(request==null) ack=="1:default" else
            (request=="1" || Regex("1:[a-f0-9]{32}").matches(request)) && ack==request
        val inset=enabled && eligible && width>465 && height>250 && width>height
        return Rect(if(inset)145 else 0,if(inset)50 else 0,width,height)
    }
}
