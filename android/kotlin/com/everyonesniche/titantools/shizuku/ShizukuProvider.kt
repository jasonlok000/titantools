package com.everyonesniche.titantools.shizuku

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import moe.shizuku.api.BinderContainer

/** Where Shizuku hands over its binder. Only shell or root can reach it (the manifest's INTERACT_ACROSS_USERS_FULL). */
class ShizukuProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        context?.let(Shizuku::init)
        return true
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method != "sendBinder" || extras == null) return null
        extras.classLoader = BinderContainer::class.java.classLoader
        @Suppress("DEPRECATION")
        val container = extras.getParcelable<BinderContainer>("moe.shizuku.privileged.api.intent.extra.BINDER")
        val binder = container?.binder
        if (binder != null) Shizuku.receive(binder, context!!.packageName)
        return Bundle()
    }

    override fun query(uri: Uri, projection: Array<String>?, selection: String?, args: Array<String>?, order: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, args: Array<String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<String>?) = 0
}
