package com.abrar.voyahiran.core.bridge
import org.junit.Assert.assertEquals
import org.junit.Test
class NeshanManeuverTest {
 @Test fun destinationWithoutStreetRetainsGuidance() {
  assertEquals("-", NeshanManeuver.roadLabel("ARRIVE", "  "))
  assertEquals(24, NeshanManeuver.icon("ARRIVE", "UNKNOWN"))
  assertEquals("Hemmat", NeshanManeuver.roadLabel("ARRIVE", "Hemmat"))
  assertEquals("-", NeshanManeuver.roadLabel("TURN", ""))
  assertEquals(0, NeshanManeuver.availableIcon("UNKNOWN", "UNKNOWN"))
  assertEquals(0, NeshanManeuver.availableIcon("TURN", "UNKNOWN"))
  assertEquals(7, NeshanManeuver.availableIcon("UNKNOWN", "LEFT"))
  assertEquals(24, NeshanManeuver.availableIcon("ARRIVE", "UNKNOWN"))
 }
 @Test fun verifiedDirections() {
  val expected = mapOf("LEFT" to 7, "SHARP_LEFT" to 6, "SLIGHT_LEFT" to 8, "RIGHT" to 3,
   "SHARP_RIGHT" to 4, "SLIGHT_RIGHT" to 2, "STRAIGHT" to 1, "U_TURN" to 5, "UNKNOWN" to 1)
  for (type in listOf("TURN","NEW_NAME","MERGE","ON_RAMP","OFF_RAMP","FORK","END_OF_ROAD",
   "CONTINUE","ROUNDABOUT_TURN","BIKE_STATION","WALK","USE_LANE","EXIT_ROUNDABOUT","UNKNOWN"))
   for ((direction, icon) in expected) assertEquals("$type/$direction",icon,NeshanManeuver.icon(type,direction))
 }
 @Test fun specialTypesOverrideDirection() {
  for (direction in listOf("LEFT","RIGHT","UNKNOWN")) {
   assertEquals(24,NeshanManeuver.icon("ARRIVE",direction))
   assertEquals(24,NeshanManeuver.icon("DEPART",direction))
   assertEquals(9,NeshanManeuver.icon("ROUNDABOUT",direction))
   assertEquals(12,NeshanManeuver.icon("NEW_EXIT_ROUNDABOUT",direction))
  }
 }
 @Test fun allRoundaboutImages() {
  val expected=listOf(9,5,4,3,2,10,8,7,6)
  expected.forEachIndexed { index,icon -> assertEquals(icon,NeshanManeuver.icon("ROTARY","LEFT",index)) }
  assertEquals(9,NeshanManeuver.icon("ROTARY","RIGHT",null))
  assertEquals(3,NeshanManeuver.icon("ROTARY","RIGHT",99))
 }
}
