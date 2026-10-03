package com.abrar.raha.feedback

/** Never shrink touch targets or introduce scrolling to fit more choices. */
object RahaPromptLayout {
    fun buttonCount(availableHeight:Int, questionHeight:Int, padding:Int, buttonHeight:Int, gap:Int):Int {
        require(buttonHeight>0 && gap>=0)
        return ((availableHeight-questionHeight-padding).coerceAtLeast(0)/(buttonHeight+gap)).coerceAtMost(4)
    }
}
