package com.abrar.voyahiran.feature.permissions

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.SpinnerAdapter
import android.widget.TextView
import com.abrar.voyahiran.R

/** Same name-and-icon row for the selected application and the dropdown. */
class InstalledAppAdapter(context: Context, private val apps: List<InstalledApp>) : BaseAdapter(), SpinnerAdapter {
    private val inflater = LayoutInflater.from(context)
    private val pm = context.packageManager
    private val icons = mutableMapOf<String, Drawable>()
    override fun getCount() = apps.size
    override fun getItem(position: Int) = apps[position]
    override fun getItemId(position: Int) = position.toLong()
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View = row(position, convertView, parent)
    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View = row(position, convertView, parent)
    private fun row(position: Int, recycled: View?, parent: ViewGroup): View {
        val view = recycled ?: inflater.inflate(R.layout.item_installed_app, parent, false)
        val app = apps[position]
        view.findViewById<TextView>(R.id.appLabel).text = app.label
        view.findViewById<ImageView>(R.id.appIcon).setImageDrawable(icons.getOrPut(app.packageName) {
            try { pm.getApplicationIcon(app.packageName) }
            catch (_: PackageManager.NameNotFoundException) { pm.defaultActivityIcon }
        })
        return view
    }
}
