package com.abrar.voyahiran.feature.navigation

import android.content.Context
import com.abrar.raha.RahaPlaces
import com.abrar.raha.RahaSavedPlace
import com.abrar.raha.RahaSavedPlaces
import org.json.JSONArray
import org.json.JSONObject

internal class SavedPlacesStore(context: Context) {
    private val prefs=context.getSharedPreferences("neshan_saved_places", Context.MODE_PRIVATE)
    fun users(): List<RahaSavedPlace> {
        val items=JSONArray(prefs.getString("places", "[]"))
        return (0 until items.length()).map { index ->
            val item=items.getJSONObject(index)
            val names=item.getJSONArray("aliases")
            RahaSavedPlace(item.getString("id"), item.getString("name"), (0 until names.length()).map(names::getString), item.getDouble("latitude"), item.getDouble("longitude"))
        }.also(RahaSavedPlaces::validate)
    }
    fun sync() = RahaPlaces.updatePlaces(users())
    fun save(place: RahaSavedPlace) = synchronized(lock) {
        val previous=users()
        val next=if(previous.any { it.id==place.id }) previous.map { if(it.id==place.id)place else it } else previous+place
        persist(next)
    }
    fun delete(id: String) = synchronized(lock) {
        require(id.startsWith("user_")) { "مکان‌های پایه قابل حذف نیستند" }
        persist(users().filterNot { it.id==id })
    }
    private fun persist(places: List<RahaSavedPlace>) {
        RahaSavedPlaces.validate(places)
        val array=JSONArray()
        for(p in places) array.put(JSONObject().put("id",p.id).put("name",p.name).put("aliases",JSONArray(p.aliases)).put("latitude",p.latitude).put("longitude",p.longitude))
        check(prefs.edit().putString("places",array.toString()).commit()) { "ذخیرهٔ مکان انجام نشد؛ دوباره امتحان کنید" }
        RahaPlaces.updatePlaces(places)
    }
    companion object { private val lock=Any() }
}
