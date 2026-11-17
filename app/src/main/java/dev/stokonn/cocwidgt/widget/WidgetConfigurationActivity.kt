package dev.stokonn.cocwidgt.widget

import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import dev.stokonn.cocwidgt.MainActivity
import dev.stokonn.cocwidgt.data.AccountStore
import dev.stokonn.cocwidgt.data.WidgetAccountStore

/** Android's native per-instance widget configuration flow. */
class WidgetConfigurationActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        val store = AccountStore.migrate(this)
        val accounts = store.accounts()
        if (accounts.isEmpty()) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }
        val prefs = getSharedPreferences("cocwidgt_widget_accounts", MODE_PRIVATE)
        val selected = accounts.indexOfFirst { it.tag == WidgetAccountStore.accountTag(prefs, widgetId, store.activeTag(), accounts.map { a -> a.tag }.toSet()) }
            .coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("Choose widget account")
            .setSingleChoiceItems(accounts.map { "${it.label}  ${it.tag}" }.toTypedArray(), selected) { dialog, which ->
                WidgetAccountStore.assign(prefs, widgetId, accounts[which].tag)
                setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
                dialog.dismiss(); finish()
            }
            .setNegativeButton("Cancel") { _, _ -> finish() }
            .show()
    }
}
