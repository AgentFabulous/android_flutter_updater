import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

class AndroidFlutterUpdater {
  static const MethodChannel _channel =
      const MethodChannel('android_flutter_updater_methods');
  static const EventChannel _events =
      const EventChannel('android_flutter_updater_events');

  static Future<String> get platformVersion async {
    final String version = await _channel.invokeMethod('getPlatformVersion');
    return version;
  }

  static registerStreamListener(
      {@required StreamSubscription subscription,
      @required Function fn,
      bool forceRegister = false}) {
    if (subscription != null && forceRegister) {
      subscription.cancel();
      subscription = null;
    }

    if (subscription == null)
      subscription = _events.receiveBroadcastStream().listen(fn);
  }

  static Future<String> getLastChecked() async {
    final String ret = await _channel.invokeMethod('getLastChecked');
    return ret;
  }

  static Future<void> checkForUpdates() async {
    await _channel.invokeMethod('checkForUpdates');
  }

  static Future<void> serviceUnbind() async {
    return await _channel.invokeMethod('serviceBind');
  }

  static Future<void> serviceBind() async {
    return await _channel.invokeMethod('serviceBind');
  }

  static Future<List<String>> getDownloads() async {
    final _ret = await _channel.invokeMethod('getDownloads');
    final List<String> ret = List<String>.from(_ret);
    return ret;
  }

  static Future<void> startDownload(String id) async {
    return await _channel
        .invokeMethod('startDownload', <String, dynamic>{'id': id});
  }

  static Future<void> pauseDownload(String id) async {
    return await _channel
        .invokeMethod('pauseDownload', <String, dynamic>{'id': id});
  }

  static Future<void> resumeDownload(String id) async {
    return await _channel
        .invokeMethod('resumeDownload', <String, dynamic>{'id': id});
  }

  static Future<void> verifyDownload(String id) async {
    return await _channel
        .invokeMethod('verifyDownload', <String, dynamic>{'id': id});
  }

  static Future<void> startUpdate(String id) async {
    return await _channel
        .invokeMethod('startUpdate', <String, dynamic>{'id': id});
  }

  static Future<Map> getNativeStatus() async {
    return await _channel.invokeMethod('getNativeStatus');
  }

  static Future<void> cancelAndDelete(String id) async {
    return await _channel
        .invokeMethod('cancelAndDelete', <String, dynamic>{'id': id});
  }

  static Future<bool> getWarn() async {
    return await _channel.invokeMethod('getWarn');
  }

  static Future<bool> needsWarn() async {
    return await _channel.invokeMethod('needsWarn');
  }

  static Future<void> setWarn(bool enable) async {
    return await _channel
        .invokeMethod('setWarn', <String, dynamic>{'enable': enable});
  }

  static Future<bool> getAutoDelete() async {
    return await _channel.invokeMethod('getAutoDelete');
  }

  static Future<void> setAutoDelete(bool enable) async {
    return await _channel
        .invokeMethod('setAutoDelete', <String, dynamic>{'enable': enable});
  }

  static Future<String> getName(String id) async {
    return await _channel.invokeMethod('getName', <String, dynamic>{'id': id});
  }

  static Future<String> getVersion(String id) async {
    return await _channel
        .invokeMethod('getVersion', <String, dynamic>{'id': id});
  }

  static Future<String> getTimestamp(String id) async {
    return await _channel
        .invokeMethod('getTimestamp', <String, dynamic>{'id': id});
  }

  static Future<String> getDeviceName() async {
    return await _channel.invokeMethod('getDeviceName');
  }

  static Future<String> getModel() async {
    return await _channel.invokeMethod('getModel');
  }

  static Future<String> getBuildVersion() async {
    return await _channel.invokeMethod('getBuildVersion');
  }

  static Future<String> getBuildDate() async {
    return await _channel.invokeMethod('getBuildDate');
  }

  static Future<String> getProp(String prop) async {
    return await _channel
        .invokeMethod('getProp', <String, dynamic>{'prop': prop});
  }

  static Future<void> installUpdate(String id) async {
    return await _channel
        .invokeMethod('installUpdate', <String, dynamic>{'id': id});
  }

  static Future<String> getChangelogUrl() async {
    return await _channel.invokeMethod('getChangelogUrl');
  }

  static Future<int> getUpdateCheckSetting() async {
    return await _channel.invokeMethod('getUpdateCheckInterval');
  }

  static Future<void> setUpdateCheckSetting(int interval) async {
    return await _channel.invokeMethod(
        'setUpdateCheckInterval', <String, dynamic>{'interval': interval});
  }
}

enum UpdateStatus {
  UNKNOWN,
  STARTING,
  DOWNLOADING,
  DOWNLOADED,
  PAUSED,
  PAUSED_ERROR,
  DELETED,
  VERIFYING,
  VERIFIED,
  VERIFICATION_FAILED,
  INSTALLING,
  INSTALLED,
  INSTALLATION_FAILED,
  INSTALLATION_CANCELLED
}
