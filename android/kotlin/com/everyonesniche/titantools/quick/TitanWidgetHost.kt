package com.everyonesniche.titantools.quick

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RemoteViews

/**
 * TitanTools' widget host. Some widgets (1Valet's, made with Glance) failed to draw in it: Android looked their layouts up in
 * the wrong app's resources ("Couldn't add widget"). Its views draw them with the widget app's own context when Android's
 * way fails, so the widget shows and its buttons work.
 */
class TitanWidgetHost(context: Context, id: Int) : AppWidgetHost(context, id) {
    override fun onCreateView(context: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo?): AppWidgetHostView =
        HostView(context)

    class HostView(context: Context) : AppWidgetHostView(context) {
        override fun updateAppWidget(remoteViews: RemoteViews?) {
            super.updateAppWidget(remoteViews)
            if (remoteViews == null || !showsError()) return
            // Android's own drawing failed: draw it with the widget app's context.
            try {
                val app = context.createPackageContext(remoteViews.`package`, Context.CONTEXT_RESTRICTED)
                val view = remoteViews.apply(app, this)
                removeAllViews()
                addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                Log.i(TAG, "Drew ${remoteViews.`package`}'s widget with its own context")
            } catch (error: Exception) {
                Log.w(TAG, "Couldn't draw ${remoteViews.`package`}'s widget (layout ${Integer.toHexString(remoteViews.layoutId)})", error)
            }
        }

        /** Android's error view ("Couldn't add widget") is a lone TextView. */
        private fun showsError(): Boolean = childCount == 1 && getChildAt(0) is android.widget.TextView &&
            (getChildAt(0) as android.widget.TextView).text?.toString()?.contains("widget", ignoreCase = true) == true
    }

    private companion object {
        const val TAG = "TitanTools"
    }
}
