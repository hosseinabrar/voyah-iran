package com.abrar.raha

import org.junit.Assert.*
import org.junit.Test

class RahaContactsTest {
    @Test fun largePhonebookExactAndPartialLookup() {
        val names=(0 until 2698).map { "مخاطب آزمایشی $it" } + listOf("میلاد نادری", "میلاد محمدی")
        val start=System.nanoTime()
        repeat(100) {
            assertEquals(listOf(2698), RahaContacts.matching("میلاد نادری", names))
            assertEquals(listOf(2698,2699), RahaContacts.matching("میلاد", names))
        }
        println("2700 contacts: average lookup ms="+(System.nanoTime()-start)/200.0/1_000_000)
    }
    @Test fun colloquialCallCountIsNotPartOfContactName() {
        for(text in listOf("یه زنگ بزن به ناهید", "رها یه زنگ بزن به ناهید", "به ناهید یه زنگ بزن",
            "لطفا یه زنگ بزن به ناهید", "با ناهید یه تماس بگیر", "یک تماس برقرار کنید با ناهید")) {
            assertEquals(text,"ناهید",RahaContacts.target(text))
        }
    }
    @Test fun firstPhoneNumberWinsWithoutCollapsingDifferentFullNames() {
        val rows=listOf("علی جعفری" to "09121111111", "علی ابراهیمی" to "09123333333",
            "علی جعفری" to "02122222222", "علی جعفری" to "09129999999", "علی اصغری" to "09124444444")
        assertEquals(listOf(rows[0],rows[1],rows[4]),RahaContacts.firstNumberPerContact(rows) { it.first })
    }
    @Test fun callWordOrders() {
        for(sentence in listOf("علی رو بگیر", "علی را بگیرید", "زنگ بزن به علی", "زنگ بزن علی", "به علی زنگ بزن", "با علی تماس بگیر", "تماس با علی", "رها زنگ بزن به علی", "سلام رها با علی تماس بگیر", "لطفا با علی تماس بگیرید",
            "با علی تماس برقرار کن", "شماره علی رو بگیر", "زنگ بزن به شماره علی", "لطفا با علی تماس برقرار کنید", "تماس برقرار کن با علی", "شماره علی را بگیرید", "با شماره علی تماس بگیر"))
            assertEquals(sentence,"علی",RahaContacts.target(sentence))
    }
    @Test fun allRequestedSentencesReachConfirmation() {
        for(sentence in listOf("با علی تماس بگیر", "علی رو بگیر", "زنگ بزن به علی", "تماس با علی", "با علی تماس برقرار کن", "شماره علی رو بگیر", "زنگ بزن به شماره علی")) {
            val flow=RahaCallDialog { 0L }
            assertEquals(sentence,RahaCallDialog.Step.Confirm(ali),flow.begin(checkNotNull(RahaContacts.target(sentence)),listOf(ali)))
        }
    }
    @Test fun emergencyAliasesAlwaysAskBeforeCalling() {
        assertEquals(setOf("110","115","125"),RahaDefaultContacts.numbers)
        for(contact in RahaDefaultContacts.entries) for(alias in contact.aliases + contact.number.map { '۰'+(it-'0') }.joinToString("")) {
            val target=RahaContacts.target("رها زنگ بزن به شماره $alias")
            assertEquals(alias,contact.name,target)
            val candidate=RahaCallDialog.Candidate(contact.number,contact.name)
            val flow=RahaCallDialog { 0L }
            assertEquals(RahaCallDialog.Step.Confirm(candidate),flow.begin(checkNotNull(target),listOf(candidate)))
            assertTrue(flow.speak("نه") is RahaCallDialog.Step.Cancelled)
        }
        assertEquals("پلیس",RahaContacts.target("تماس با ١١٠"))
        assertNull(RahaDefaultContacts.find("111"))
        assertNull(RahaDefaultContacts.find("پلیس علی"))
    }
    @Test fun exactNameWinsAndPartialMatchesAreWholeWords() {
        assertEquals(listOf(0),RahaContacts.matching("علی",listOf("علی","پدر علی","مادر علی")))
        assertEquals(listOf(0,1,2),RahaContacts.matching("علی",listOf("علی جعفری","علی ابراهیمی","علی اصغری","علیزاده")))
        assertEquals(emptyList<Int>(),RahaContacts.matching("",listOf("علی")))
        assertEquals(listOf(0),RahaContacts.matching("علی پور",listOf("علی‌پور")))
    }
    private val ali=RahaCallDialog.Candidate("1","علی")
    @Test fun everySpokenSelectionRequiresExplicitConfirmation() {
        var time=0L
        val flow=RahaCallDialog { time }
        assertTrue(flow.begin("علی",listOf(ali)) is RahaCallDialog.Step.Confirm)
        assertTrue(flow.speak("نه بله") is RahaCallDialog.Step.Cancelled)
        for(no in listOf("", "نه", "خیر", "کنسل", "نه درسته نیست", "علی", "بله نه", "[unk]", "آهنگ بعدی")) {
            flow.begin("علی",listOf(ali)); assertTrue(no,flow.speak(no) is RahaCallDialog.Step.Cancelled)
        }
        for(yes in RahaContacts.affirmative) {
            flow.begin("علی",listOf(ali)); assertEquals(RahaCallDialog.Step.Dial(ali),flow.speak(yes))
            assertTrue(flow.speak(yes) is RahaCallDialog.Step.Cancelled)
        }
        flow.begin("علی",listOf(ali)); time=10_000
        assertTrue(flow.speak("بله") is RahaCallDialog.Step.Cancelled)
    }
    @Test fun ambiguitySpokenFullNameThenConfirmButCallButtonConfirmsDirectly() {
        val candidates=listOf(RahaCallDialog.Candidate("1","علی جعفری"),RahaCallDialog.Candidate("2","علی ابراهیمی"),RahaCallDialog.Candidate("3","علی اصغری"))
        val flow=RahaCallDialog { 0L }
        assertTrue(flow.begin("علی",candidates) is RahaCallDialog.Step.Choose)
        assertEquals(RahaCallDialog.Step.Confirm(candidates[0]),flow.speak("علی جعفری"))
        assertEquals(RahaCallDialog.Step.Dial(candidates[0]),flow.speak("درسته"))
        flow.begin("علی",candidates)
        assertEquals(RahaCallDialog.Step.Dial(candidates[1]),flow.select("2"))
        assertTrue(flow.select("2") is RahaCallDialog.Step.Cancelled)
    }
    @Test fun staleTouchesAndUnknownIdsCannotDial() {
        var time=0L
        val flow=RahaCallDialog { time }
        flow.begin("علی",listOf(ali)); assertTrue(flow.select("missing") is RahaCallDialog.Step.Cancelled)
        flow.begin("علی",listOf(ali)); flow.cancel(); assertTrue(flow.select("1") is RahaCallDialog.Step.Cancelled)
        flow.begin("علی",listOf(ali)); time=10_000; assertTrue(flow.select("1") is RahaCallDialog.Step.Cancelled)
    }
    @Test fun updatedNamesReplacePreviousVocabulary() {
        try {
            RahaContacts.update(listOf("عالی")); val revision=RahaContacts.revision
            RahaContacts.update(listOf("علی"))
            assertTrue(RahaContacts.revision>revision)
            assertFalse("عالی" in RahaContacts.vocabulary())
            assertTrue("با علی تماس بگیر" in RahaSpeechVocabulary.phrases())
            RahaContacts.update(emptyList()); assertTrue(RahaContacts.vocabulary().isEmpty())
        } finally { RahaContacts.update(emptyList()) }
    }
}
