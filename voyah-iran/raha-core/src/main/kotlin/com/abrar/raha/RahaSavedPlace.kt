package com.abrar.raha

data class RahaSavedPlace(val id: String, val name: String, val aliases: List<String>, val latitude: Double, val longitude: Double)

object RahaSavedPlaces {
    fun aliases(text: String): List<String> = text.split(',', '،').map(String::trim).filter(String::isNotEmpty)
        .distinctBy { com.abrar.raha.intent.RahaWordPiece.normalize(it) }.also { require(it.size <= 4) { "حداکثر چهار نام جایگزین وارد کنید" } }

    fun validate(places: List<RahaSavedPlace>) {
        require(places.size <= 18) { "حداکثر ۱۸ مکان شخصی می‌توانید ذخیره کنید" }
        require(places.map { it.id }.distinct().size == places.size) { "شناسهٔ مکان تکراری است" }
        val owners=mutableMapOf<String,String>()
        RahaPlaces.builtInAliases.forEach { (id,names) -> names.forEach { owners[com.abrar.raha.intent.RahaWordPiece.normalize(it)] = id } }
        for(place in places) {
            require(place.id.matches(Regex("user_[a-zA-Z0-9-]+"))) { "شناسهٔ مکان معتبر نیست" }
            require(place.name.isNotBlank() && place.name.length <= 60) { "نام مکان را با حداکثر ۶۰ حرف وارد کنید" }
            require(place.aliases.size <= 4) { "حداکثر چهار نام جایگزین وارد کنید" }
            require(place.latitude.isFinite() && place.latitude in -90.0..90.0 && place.longitude.isFinite() && place.longitude in -180.0..180.0) { "موقعیت مکان معتبر نیست" }
            for(name in listOf(place.name)+place.aliases) {
                val normalized=com.abrar.raha.intent.RahaWordPiece.normalize(name)
                require(normalized.isNotBlank() && name.length <= 60 && name.none { it == ',' || it == '،' || it.isISOControl() }) { "نام یا نام جایگزین معتبر نیست" }
                require(owners[normalized] == null || owners[normalized] == place.id) { "نام «$name» برای مکان دیگری ثبت شده است" }
                owners[normalized]=place.id
            }
        }
    }
}
