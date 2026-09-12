package com.abrar.voyahiran.feature.apollo.data

import android.content.Context
import android.net.Uri
import com.abrar.voyahiran.feature.apollo.domain.*
import com.abrar.voyahiran.feature.drivememory.data.CanDriveGateway
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

/** Read-only: provider query + CAN getters. No command runner or write method. */
class ApolloReportReader(context: Context) {
    private val resolver = context.applicationContext.contentResolver
    private val gateway = CanDriveGateway(context) {}
    suspend fun read(): ApolloReport {
        val subscription = runCatching {
            resolver.query(Uri.parse("content://com.pateo.baiduStateProvider/baidu_state/subscribe"),
                arrayOf("type", "content"), "type=?", arrayOf("1"), "id asc")?.use { cursor ->
                if (!cursor.moveToFirst()) null else {
                    val json = JSONObject(cursor.getString(cursor.getColumnIndexOrThrow("content")))
                    subscriptionReport(json.optString("subscriptionStatus"), json.optString("expireStatus"))
                }
            }
        }.onFailure { com.abrar.voyahiran.core.logging.AppLog.error("Apollo", "subscription-provider-read-failed", it) }.getOrNull()
        com.abrar.voyahiran.core.logging.AppLog.changed("ApolloSubscription", "active=$subscription")
        val values = mutableMapOf<ApolloFeature, Boolean?>()
        try {
            for (feature in ApolloFeature.entries) {
                values[feature] = try { feature.checked(gateway.readReportState(feature.ordinalId, feature.stateId)) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) {
                    com.abrar.voyahiran.core.logging.AppLog.error("Apollo", "feature-read-failed feature=$feature", error)
                    null
                }
                com.abrar.voyahiran.core.logging.AppLog.changed("Apollo/$feature", "checked=${values[feature]}")
            }
        } finally { gateway.close() }
        return ApolloReport(subscription, values)
    }
}
