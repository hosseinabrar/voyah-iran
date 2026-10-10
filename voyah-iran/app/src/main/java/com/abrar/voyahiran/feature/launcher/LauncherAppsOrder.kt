package com.abrar.voyahiran.feature.launcher

/** A dense sequence: moving inserts at the destination and shifts intervening apps. */
internal object LauncherAppsOrder {
    fun merge(saved:List<String>, available:List<String>):List<String> {
        val present=available.toSet()
        return (saved.filter {it in present}+available).distinct()
    }
    fun move(ids:List<String>, id:String, destination:Int):List<String> {
        if(id !in ids)return ids
        return ids.toMutableList().apply {remove(id);add(destination.coerceIn(0,size),id)}
    }
}
