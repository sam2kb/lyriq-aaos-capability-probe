package com.sam2kb.lyriqprobe.core.storage

import android.content.Context
import android.net.Uri

class SelectedTreeStore(context: Context) {
    private val prefs = context.getSharedPreferences("saf_tree", Context.MODE_PRIVATE)

    fun load(): Uri? = prefs.getString(KEY_URI, null)?.let(Uri::parse)

    fun save(uri: Uri) {
        prefs.edit().putString(KEY_URI, uri.toString()).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_URI).apply()
    }

    private companion object {
        const val KEY_URI = "tree_uri"
    }
}
