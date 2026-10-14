package com.abrar.voyahiran.feature.subscription

import android.app.*
import android.content.*
import android.net.*
import android.os.IBinder
import com.abrar.voyahiran.R
import com.abrar.voyahiran.VoyahIranApplication

class SubscriptionService : Service() {
    private val subscriptions get()=(application as VoyahIranApplication).subscriptions
    private val network=object: ConnectivityManager.NetworkCallback() { override fun onAvailable(network: Network) { subscriptions.refresh() } }
    private val screen=object: BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_ON || intent.action == Intent.ACTION_USER_PRESENT)
                subscriptions.screenAwake()
        }
    }
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("subscription",getString(R.string.subscription_title),NotificationManager.IMPORTANCE_LOW))
        val open=PendingIntent.getActivity(this,46,Intent(this,SubscriptionActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        startForeground(46,Notification.Builder(this,"subscription").setSmallIcon(R.drawable.ic_app).setContentTitle(getString(R.string.app_name)).setContentText(getString(R.string.subscription_background)).setContentIntent(open).build())
        getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(network)
        registerReceiver(screen, IntentFilter(Intent.ACTION_SCREEN_ON).apply { addAction(Intent.ACTION_USER_PRESENT) })
        subscriptions.start()
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int { subscriptions.start(); return START_STICKY }
    override fun onBind(intent: Intent?): IBinder?=null
    override fun onDestroy() { unregisterReceiver(screen);getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(network);super.onDestroy() }
    companion object { fun start(context: Context) { context.startForegroundService(Intent(context,SubscriptionService::class.java)) } }
}
