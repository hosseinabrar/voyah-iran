package com.abrar.voyahiran.feature.launcher
import org.junit.Assert.*
import org.junit.Test
class LauncherAppsOrderTest {
 @Test fun crossPageMovesRemainDense() {
  val ids=(0..60).map {"app$it"}
  val moved=LauncherAppsOrder.move(ids,"app2",54)
  assertEquals("app2",moved[54]);assertEquals("app3",moved[2])
  assertEquals(ids.size,moved.size);assertEquals(ids.toSet(),moved.toSet())
  assertEquals(ids,LauncherAppsOrder.move(moved,"app2",2))
 }
 @Test fun refreshKeepsOrderDropsRemovedAndAppendsNew() {
  assertEquals(listOf("c","a","d"),LauncherAppsOrder.merge(listOf("c","b","a","c"),listOf("a","c","d")))
 }
 @Test fun emptyLastPageCellMeansEndWithoutGaps() {
  assertEquals(listOf("b","c","a"),LauncherAppsOrder.move(listOf("a","b","c"),"a",26))
 }
}
