package com.abrar.raha

/** Iran public emergency numbers. These still use the normal explicit-confirmation dialogue. */
object RahaDefaultContacts {
    data class Contact(val name:String,val number:String,val aliases:List<String>)
    val entries=listOf(
        Contact("پلیس","110",listOf("پلیس","110","صد و ده","صد ده","یک یک صفر")),
        Contact("اورژانس","115",listOf("اورژانس","115","صد و پانزده","صد پانزده","یک یک پنج")),
        Contact("آتش نشانی","125",listOf("آتش نشانی","آتش‌نشانی","آتشنشانی","اتش نشانی","اتشنشانی","125","صد و بیست و پنج","صد بیست و پنج","یک دو پنج"))
    )
    val numbers=entries.map { it.number }.toSet()
    fun find(alias:String):Contact? = entries.firstOrNull { c -> c.aliases.any { RahaContacts.normalize(it)==RahaContacts.normalize(alias) } }
}
