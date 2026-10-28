package com.abrar.voyahiran.feature.launcher

import java.io.File

/** Maximum audited consumers are 1920px launcher surfaces; retain sampling headroom. */
internal object AssetSizingPlan {
    data class Entry(val file:File,val oldWidth:Int,val oldHeight:Int,val width:Int,val height:Int,val displayWidth:Int,val displayHeight:Int)
    fun entries():List<Entry> = buildList {
        File("src/main/assets/passenger/fragrance-wide").listFiles().orEmpty()
            .filter{it.name.matches(Regex("object-[123](-day-v[12])?(-gray)?\\.png"))}
            .forEach{add(Entry(it,1263,1246,512,505,185,183))}
        File("src/main/assets/passenger/oem-x").listFiles().orEmpty().forEach{
            when {
                it.name.matches(Regex("seat_vent_(left|right)_\\d+\\.png"))->add(Entry(it,500,500,250,250,149,149))
                it.name.matches(Regex("seat_(main|co)_massage_(rolling|waving)_\\d+\\.png"))->add(Entry(it,150,200,75,100,45,60))
            }
        }
        for(name in listOf("voyah_iran_icon","launcher_voyah_icon_light"))
            add(Entry(File("src/main/res/drawable-nodpi/$name.png"),1254,1254,512,512,256,256))
        // Isolated cluster tester draws these into a 512px canvas, then a 480px ImageView.
        for(name in listOf("cluster_music_test","cluster_map_test"))
            add(Entry(File("../../voyah-cluster-test/app/src/main/assets/$name.png"),1254,1254,512,512,512,512))
    }.sortedBy{it.file.path}
    fun backupFile(root:File,entry:Entry):File {
        val repo=File("../..").canonicalFile
        return File(root,entry.file.canonicalFile.relativeTo(repo).path)
    }
}
