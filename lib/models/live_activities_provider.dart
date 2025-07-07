/// Represents the different provider types for Live Activities across platforms
enum LiveActivitiesProvider {
  /// iOS Live Activities (iOS 16.1+)
  iosLiveActivity,

  /// Android Enhanced Live Updates with improved performance (Android 12+/API 31+)
  androidEnhancedLiveUpdate,

  /// Android Basic Live Updates with efficient notification updates (Android 8.0-11/API 26-30)
  androidBasicLiveUpdate,

  /// Android RemoteViews for backward compatibility (Android 7.0-7.1/API 24-25)
  androidRemoteView,
}