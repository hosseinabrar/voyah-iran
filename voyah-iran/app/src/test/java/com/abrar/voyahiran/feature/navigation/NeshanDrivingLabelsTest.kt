package com.abrar.voyahiran.feature.navigation
import org.junit.Assert.*
import org.junit.Test
class NeshanDrivingLabelsTest {
 @Test fun spacesAndArabicLettersMatch() {
  assertEquals("سایر",NeshanDrivingLabels.normalize(" ساير "))
  assertEquals("ورود به حالت رانندگی",NeshanDrivingLabels.normalize("ورود‌به‌حالت‌رانندگي"))
 }
}
