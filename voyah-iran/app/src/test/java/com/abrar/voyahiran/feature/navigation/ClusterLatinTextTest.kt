package com.abrar.voyahiran.feature.navigation
import org.junit.Assert.assertEquals
import org.junit.Test
class ClusterLatinTextTest {
 @Test fun removesRequestedTitlesBeforeTransliteration() {
  for (prefix in listOf("کوچه", "خیابان", "اتوبان", "بزرگراه", "کنارگذر", "دسترسی محلی", "زیر گذر", "پل", "پل طبقاتی", "کندرو", "شهید", "آیت الله", "حجت الاسلام", "دکتر", "سید"))
   assertEquals(prefix, "hemmat", ClusterLatinText.convert("$prefix همت"))
  assertEquals("hemmat", ClusterLatinText.convert("بزرگراه شهید همت"))
 }
 @Test fun handlesSpacingAndArabicVariants() {
  for (title in listOf("آیت‌الله", "آیتالله", "حجت‌الاسلام", "حجتالاسلام", "زیرگذر", "زیر‌گذر", "کنار‌گذر", "کند‌رو", "دسترسی‌محلی", "پل‌طبقاتی", "کنار گذر", "کند رو", "پل   طبقاتی", "سَيِّد"))
   assertEquals(title,"همت",ClusterLatinText.shortRoadName("$title همت"))
  assertEquals("کاشانی",ClusterLatinText.shortRoadName("آيت الله كاشاني"))
 }
 @Test fun preservesMeaningfulNamesAndExceptions() {
  assertEquals("تونل همت",ClusterLatinText.shortRoadName("تونل شهید همت"))
  assertEquals("میدان همت",ClusterLatinText.shortRoadName("میدان شهید همت"))
  for (name in listOf("شهیدی", "سیدان", "پلور", "دکتران", "خیابانی")) {
   assertEquals(name,ClusterLatinText.shortRoadName(name))
  }
  assertEquals("meydan hemmat",ClusterLatinText.convert("میدان همت"))
  assertEquals("tunnel hemmat",ClusterLatinText.convert("تونل همت"))
  assertEquals("",ClusterLatinText.convert("پل طبقاتی"))
  assertEquals("12",ClusterLatinText.convert("کوچه ۱۲"))
 }
 @Test fun preservesStandaloneshohadaAndRemovesHonorifics() {
  for (word in listOf("شهدا", "شهداء", "شهدای")) {
   assertEquals(word,ClusterLatinText.shortRoadName(word))
   assertEquals(word,ClusterLatinText.shortRoadName("خیابان $word"))
   assertEquals("میدان $word",ClusterLatinText.shortRoadName("میدان $word"))
   assertEquals("هفتم تیر",ClusterLatinText.shortRoadName("بلوار $word هفتم تیر"))
   assertEquals("هفتم تیر",ClusterLatinText.shortRoadName("${word}‌هفتم‌تیر"))
  }
  assertEquals("shohada",ClusterLatinText.convert("خیابان شهدا"))
  for (title in listOf("بلوار", "سردار", "استاد", "پروفسور"))
   assertEquals("hemmat",ClusterLatinText.convert("$title همت"))
  assertEquals("شهدا",ClusterLatinText.shortRoadName("خیابان شهدا شمالی"))
  assertEquals("تونل شهدا",ClusterLatinText.shortRoadName("تونل شهدا"))
 }
 @Test fun tehranNamesAndUserExamples() {
  val samples = listOf(
   "بزرگراه چمران" to "chamran",
   "خیابان مدیریت" to "modiriat",
   "خیابان وزرا" to "vozara",
   "کوچه سی و ششم" to "si o sheshom",
   "بزرگراه شهید همت شرقی" to "hemmat",
   "بزرگراه آل‌احمد" to "al ahmad",
   "بزرگراه آیت‌الله سعیدی" to "saeidi",
   "بزرگراه حقانی" to "haghani",
   "بزرگراه مدرس" to "modarres",
   "بلوار کشاورز" to "keshavarz",
   "خیابان وحید دستجردی" to "vahid dastjerdi",
   "خیابان ظفر" to "zafar",
   "خیابان ۱۵ خرداد" to "15 khordad",
   "خیابان ۱۶ آذر" to "16 azar",
   "خیابان ۱۷ شهریور" to "17 shahrivar",
   "خیابان آزادی" to "azadi",
   "خیابان استاد حسن بنا" to "hasan banna",
   "خیابان اسکندری شمالی" to "eskandari",
   "خیابان اقبال لاهوری" to "eghbal lahouri",
   "خیابان امام خمینی" to "emam khomeini",
   "خیابان امیرکبیر" to "amirkabir",
   "خیابان انقلاب" to "enghelab",
   "خیابان آیت الله مدنی" to "madani",
   "خیابان باهنر" to "bahonar",
   "خیابان بهشتی" to "beheshti",
   "خیابان پاتریس لومومبا" to "patris lumumba",
   "خیابان پاسداران" to "pasdaran",
   "خیابان پیروزی" to "pirouzi",
   "خیابان توحید" to "tohid",
   "خیابان جانبازان" to "janbazan",
   "خیابان جمهوری" to "jomhouri",
   "خیابان خالد اسلامبولی" to "khaled eslamboli",
   "خیابان خدامی" to "khodami",
   "خیابان خرمشهر" to "khorramshahr",
   "خیابان رجایی" to "rajaei",
   "خیابان قرنی" to "gharani",
   "خیابان ستارخان" to "sattarkhan",
   "خیابان سعدی" to "saadi",
   "خیابان سمیه" to "somayyeh",
   "خیابان سهروردی جنوبی" to "sohrevardi",
   "خیابان شریعتی" to "shariati",
   "خیابان شهید دیباجی" to "dibaji",
   "خیابان طالقانی" to "taleghani",
   "خیابان فاطمی" to "fatemi",
   "خیابان ولی‌عصر" to "valiasr",
   "خیابان مطهری" to "motahhari",
   "خیابان میرداماد" to "mirdamad",
   "خیابان کارگر شمالی" to "kargar",
   "میدان شهدا" to "meydan shohada",
   "تونل رسالت" to "tunnel resalat",
   "خیابان علامه طباطبایی جنوبی" to "allameh tabatabaei",
   "کوچه بیست و سوم" to "bist o sevom",
   "کوچه چهل و دوم" to "chehel o dovom",
   "کوچه هفتاد و هشتم" to "haftad o hashtom",
   "کوچه سی‌ام" to "siom",
   "خیابان شهدای هفتم تیر" to "haftom tir",
   "بزرگراه شهید همت غربی" to "hemmat",
   "خیابان مُدِیریَت" to "modiriat",
   "هَمَّت" to "hemmat",
   "وُزَرا" to "vozara",
   "چَمران" to "chamran",
   "کوچه بَژ" to "bazh",
   "کوچه بِژ" to "bezh",
   "کوچه بُژ" to "bozh",
   "کوچه بَژّ" to "bazhzh"
  )
  for ((input, expected) in samples) assertEquals(input, expected, ClusterLatinText.convert(input))
 }
 @Test fun stripsDirectionsAtWordBoundaries() {
  for (direction in listOf("شرقی", "غربی", "شمالی", "جنوبی")) {
   assertEquals("hemmat",ClusterLatinText.convert("همت $direction"))
   assertEquals("hemmat",ClusterLatinText.convert("همت‌${direction}"))
  }
  assertEquals("شمالیان",ClusterLatinText.shortRoadName("خیابان شمالیان"))
  assertEquals("شهدا",ClusterLatinText.shortRoadName("خیابان شهدا جنوبی"))
 }
}
