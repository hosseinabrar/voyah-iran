package com.abrar.voyahiran.feature.launcher

/** Frame-callback gaps, not a claim of GPU presentation FPS. Active only during a swipe. */
internal class LauncherSwipeFrames {
    private var previous:Long?=null
    var intervals=0;private set
    var slow=0;private set
    var maxGapNanos=0L;private set
    fun frame(at:Long){
        previous?.let{last->
            if(at>last){val gap=at-last;intervals++;if(gap>32_000_000)slow++;maxGapNanos=maxOf(maxGapNanos,gap)}
        }
        previous=at
    }
    fun reset(){previous=null;intervals=0;slow=0;maxGapNanos=0}
}
