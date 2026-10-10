package com.abrar.voyahiran.feature.navigation
import org.junit.Assert.*
import org.junit.Test
class NeshanStartupGateTest {
 @Test fun splashAndSingleFrameDoNotPermitDestination() {
  val gate=NeshanStartupGate()
  gate.observe(false,0); assertFalse(gate.ready(500))
  gate.observe(true,600); assertFalse(gate.ready(950))
  gate.observe(true,950); assertTrue(gate.ready(950))
 }
 @Test fun disappearanceOrStaleWindowRequiresFreshReadiness() {
  val gate=NeshanStartupGate()
  gate.observe(true,0); gate.observe(true,350); assertTrue(gate.ready(350))
  assertFalse(gate.ready(1500))
  gate.observe(true,1600); assertFalse(gate.ready(1600))
  gate.observe(true,1950); assertTrue(gate.ready(1950))
  gate.observe(false,2000); assertFalse(gate.ready(2000))
 }
}
