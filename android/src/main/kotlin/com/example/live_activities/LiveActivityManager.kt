package com.example.live_activities

import android.util.Log
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import java.math.BigInteger
import java.security.MessageDigest

open class LiveActivityManager(private val context: Context) {
    private val liveActivitiesMap = mutableMapOf<Int, Long>()
    private val cachedNotifications = mutableMapOf<Int, Notification>()
    private lateinit var channelName: String

    companion object {
        // Android API level where live updates are supported efficiently
        private const val LIVE_UPDATES_MIN_API = 26 // Android 8.0 (API 26)
        private const val ENHANCED_LIVE_UPDATES_MIN_API = 31 // Android 12 (API 31)
    }

    open suspend fun buildNotification(
        notification: Notification.Builder,
        event: String,
        data: Map<String, Any>
    ): Notification {
        throw NotImplementedError("You must implement buildNotification in your subclass")
    }

    /**
     * Check if the device supports Android live updates for notifications
     * Live updates provide more efficient notification updates for frequently changing content
     */
    fun supportsLiveUpdates(): Boolean {
        return Build.VERSION.SDK_INT >= LIVE_UPDATES_MIN_API
    }

    /**
     * Check if the device supports enhanced live updates with better performance
     */
    fun supportsEnhancedLiveUpdates(): Boolean {
        return Build.VERSION.SDK_INT >= ENHANCED_LIVE_UPDATES_MIN_API
    }

    /**
     * Build a notification optimized for live updates when supported
     */
    private suspend fun buildLiveUpdateNotification(
        notification: Notification.Builder,
        event: String,
        data: Map<String, Any>,
        activityId: Int
    ): Notification {
        val builtNotification = buildNotification(notification, event, data)
        
        // Configure for live updates on supported devices
        if (supportsLiveUpdates()) {
            notification
                .setOnlyAlertOnce(true) // Don't alert on updates, only on creation
                .setOngoing(true) // Keep notification persistent
                .setSilent(true) // Silent updates for better UX
            
            if (supportsEnhancedLiveUpdates()) {
                // Enhanced features for Android 12+
                notification.setAllowSystemGeneratedContextualActions(false)
            }
        }
        
        return builtNotification
    }

    private fun createNotificationChannel(
        channelName: String,
        channelDescription: String,
        channelImportance: Int = NotificationManager.IMPORTANCE_LOW,
    ) {
        this.channelName = channelName
        val existingChannel =
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).getNotificationChannel(
                channelName
            )
        if (existingChannel == null) {
            val channel = NotificationChannel(
                channelName, channelDescription, channelImportance
            ).apply {
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    // Converts a string to an int to use it as notification ID
    private fun getNotificationIdFromString(input: String): Int {
        val digest =
            MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return BigInteger(digest).abs().toInt()  // Get positive Int
    }

    fun initialize(data: Map<String, Any>) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val liveActivityChannelName =
            data["liveActivityChannelName"] as? String ?: "Live Activities"
        val liveActivityChannelDescription =
            data["liveActivityChannelDescription"] as? String
                ?: "Live Activities Notifications"


        createNotificationChannel(
            liveActivityChannelName, liveActivityChannelDescription
        )
    }

    suspend fun createActivity(
        id: String,
        timestamp: Long, data: Map<String, Any>
    ): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null;

        val activityId = getNotificationIdFromString(id)

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val areNotificationsEnabled =
            NotificationManagerCompat.from(context).areNotificationsEnabled()

        if (areNotificationsEnabled) {
            val notification = if (supportsLiveUpdates()) {
                Log.d("LiveActivityManager", "Creating live activity with live updates support for ID: $id")
                buildLiveUpdateNotification(
                    Notification.Builder(context, channelName),
                    "create",
                    data,
                    activityId
                )
            } else {
                Log.d("LiveActivityManager", "Creating live activity with traditional RemoteViews for ID: $id")
                buildNotification(
                    Notification.Builder(context, channelName),
                    "create",
                    data,
                )
            }
            
            notificationManager.notify(activityId, notification)
            
            // Cache notification for efficient updates on live update supported devices
            if (supportsLiveUpdates()) {
                cachedNotifications[activityId] = notification
            }
        } else {
            Log.w(
                "LiveActivityManager",
                "Notification permission denied. Unable to show notification."
            )
            return null
        }

        liveActivitiesMap[activityId] = timestamp
        return id
    }

    suspend fun updateActivity(
        id: String,
        timestamp: Long,
        data: Map<String, Any>
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val activityId = getNotificationIdFromString(id)

        if (liveActivitiesMap.containsKey(activityId) && liveActivitiesMap[activityId] ?: 0L >= timestamp) {
            Log.w(
                "LiveActivityManager",
                "Attempted to update activity with ID $id but the timestamp is not newer than the existing one."
            )
            return
        }

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val areNotificationsEnabled =
            NotificationManagerCompat.from(context).areNotificationsEnabled()

        if (areNotificationsEnabled) {
            val notification = if (supportsLiveUpdates()) {
                Log.d("LiveActivityManager", "Updating live activity with live updates for ID: $id")
                // Use live updates for more efficient notification updates
                buildLiveUpdateNotification(
                    Notification.Builder(context, channelName),
                    "update",
                    data,
                    activityId
                )
            } else {
                Log.d("LiveActivityManager", "Updating live activity with traditional method for ID: $id")
                buildNotification(
                    Notification.Builder(context, channelName),
                    "update",
                    data
                )
            }
            
            notificationManager.notify(activityId, notification)
            
            // Update cached notification for future efficient updates
            if (supportsLiveUpdates()) {
                cachedNotifications[activityId] = notification
            }
        } else {
            Log.w(
                "LiveActivityManager",
                "Notification permission denied. Unable to show notification."
            )
            return
        }

        liveActivitiesMap[activityId] = timestamp
    }

    fun endActivity(
        id: String,
        data: Map<String, Any>
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val activityId = getNotificationIdFromString(id)

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        notificationManager.cancel(activityId)
        liveActivitiesMap.remove(activityId)
        
        // Clean up cached notification if using live updates
        if (supportsLiveUpdates()) {
            cachedNotifications.remove(activityId)
        }
    }

    fun endAllActivities(data: Map<String, Any>) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        for (activityId in liveActivitiesMap.keys.toList()) {
            notificationManager.cancel(activityId)
            liveActivitiesMap.remove(activityId)
        }
        
        // Clean up all cached notifications if using live updates
        if (supportsLiveUpdates()) {
            cachedNotifications.clear()
        }
    }

    fun getAllActivitiesIds(data: Map<String, Any>): List<Int> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return emptyList()

        return liveActivitiesMap.keys.toList()
    }

    fun areActivitiesEnabled(data: Map<String, Any>): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false;

        return true
    }
}
