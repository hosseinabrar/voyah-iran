package com.abrar.voyahiran.core.bridge

/** Bounded normalized coordinates; never accepts a caller-selected physical screen. */
internal data class MapTouchPacket(val down:Long,val time:Long,val action:Int,val points:List<Point>) {
    data class Point(val id:Int,val x:Float,val y:Float)
    companion object {
        fun parse(raw:String):MapTouchPacket? = runCatching {
            require(raw.length<=256)
            val parts=raw.split(';');require(parts.size in 4..8)
            val down=parts[0].toLong();val time=parts[1].toLong();val action=parts[2].toInt()
            require(down>0 && time>=down)
            val points=parts.drop(3).map { item ->
                val p=item.split(',');require(p.size==3)
                Point(p[0].toInt(),p[1].toFloat(),p[2].toFloat()).also {
                    require(it.id in 0..31 && it.x.isFinite() && it.y.isFinite() && it.x in 0f..1f && it.y in 0f..1f)
                }
            }
            require(points.map {it.id}.distinct().size==points.size)
            val masked=action and 255;val index=action shr 8
            require(masked in listOf(0,1,2,3,5,6) && index in points.indices)
            require(if(masked==5 || masked==6) points.size>=2 else index==0)
            require(masked !in listOf(0,1) || points.size==1)
            MapTouchPacket(down,time,action,points)
        }.getOrNull()
    }
}
