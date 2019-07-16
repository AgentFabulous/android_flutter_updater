import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

class AndroidFlutterUpdater {
  static const MethodChannel _channel =
      const MethodChannel('android_flutter_updater/methods');
  static const EventChannel _events =
      const EventChannel('android_flutter_updater/events');

  static Future<String> get platformVersion async {
    final String version = await _channel.invokeMethod('getPlatformVersion');
    return version;
  }

  static Stream _broadcastStream;

  static StreamSubscription registerStreamListener({
    @required StreamSubscription streamSubscription,
    @required Function onData,
  }) {
    if (streamSubscription != null) {
      streamSubscription.cancel();
    }

    if (_broadcastStream == null) {
      _broadcastStream = _events.receiveBroadcastStream();
    }
    return _broadcastStream.listen(onData);
  }

  // Download ID methods
  static Future<void> startDownload(String id) async =>
      await _channel.invokeMethod('startDownload', {'id': id});

  static Future<void> pauseDownload(String id) async =>
      await _channel.invokeMethod('pauseDownload', {'id': id});

  static Future<void> resumeDownload(String id) async =>
      await _channel.invokeMethod('resumeDownload', {'id': id});

  static Future<void> verifyDownload(String id) async =>
      await _channel.invokeMethod('verifyDownload', {'id': id});

  static Future<void> startUpdate(String id) async =>
      await _channel.invokeMethod('startUpdate', {'id': id});

  static Future<void> cancelAndDelete(String id) async =>
      await _channel.invokeMethod('cancelAndDelete', {'id': id});

  static Future<String> getName(String id) async =>
      await _channel.invokeMethod('getName', {'id': id});

  static Future<String> getVersion(String id) async =>
      await _channel.invokeMethod('getVersion', {'id': id});

  static Future<String> getTimestamp(String id) async =>
      await _channel.invokeMethod('getTimestamp', {'id': id});

  static Future<void> installUpdate(String id) async =>
      await _channel.invokeMethod('installUpdate', {'id': id});

  static Future<int> getDownloadProgress(String id) async =>
      await _channel.invokeMethod('getDownloadProgress', {'id': id});

  static Future<UpdateStatus> getStatus(String id) async =>
      _strToStatusEnum(await _channel.invokeMethod('getStatus', {'id': id}));

  static Future<int> getPersistentStatus(String id) async =>
      await _channel.invokeMethod('getPersistentStatus', {'id': id});

  static Future<String> getEta(String id) async =>
      await _channel.invokeMethod('getEta', {'id': id});

  static Future<String> getSpeed(String id) async =>
      await _channel.invokeMethod('getSpeed', {'id': id});

  static Future<String> getSize(String id) async =>
      await _channel.invokeMethod('getSize', {'id': id});

  // Update service methods
  static Future<void> checkForUpdates() async =>
      await _channel.invokeMethod('checkForUpdates');

  static Future<void> serviceUnbind() async =>
      await _channel.invokeMethod('serviceBind');

  static Future<void> serviceBind() async =>
      await _channel.invokeMethod('serviceBind');

  static Future<List<String>> getDownloads() async {
    final _ret = await _channel.invokeMethod('getDownloads');
    return _ret == null ? List() : List<String>.from(_ret);
  }

  static Future<String> getLastChecked() async =>
      await _channel.invokeMethod('getLastChecked');

  static Future<bool> getWarn() async => await _channel.invokeMethod('getWarn');

  static Future<bool> needsWarn() async =>
      await _channel.invokeMethod('needsWarn');

  static Future<void> setWarn(bool enable) async =>
      await _channel.invokeMethod('setWarn', {'enable': enable});

  static Future<bool> getAutoDelete() async =>
      await _channel.invokeMethod('getAutoDelete');

  static Future<void> setAutoDelete(bool enable) async =>
      await _channel.invokeMethod('setAutoDelete', {'enable': enable});

  static Future<String> getDeviceName() async =>
      await _channel.invokeMethod('getDeviceName');

  static Future<String> getModel() async =>
      await _channel.invokeMethod('getModel');

  static Future<String> getBuildVersion() async =>
      await _channel.invokeMethod('getBuildVersion');

  static Future<String> getBuildDate() async =>
      await _channel.invokeMethod('getBuildDate');

  static Future<String> getReleaseType() async =>
      await _channel.invokeMethod('getReleaseType');

  static Future<void> setReleaseType(String type) async =>
      await _channel.invokeMethod('setReleaseType', {'type': type});

  static Future<bool> getVerify() async =>
      await _channel.invokeMethod('getVerify');

  static Future<void> setVerify(bool enable) async =>
      await _channel.invokeMethod('setVerify', {'enable': enable});

  static Future<String> getProp(String prop) async =>
      await _channel.invokeMethod('getProp', {'prop': prop});

  static Future<int> getUpdateCheckSetting() async =>
      await _channel.invokeMethod('getUpdateCheckInterval');

  static Future<void> setUpdateCheckSetting(int interval) async =>
      await _channel
          .invokeMethod('setUpdateCheckInterval', {'interval': interval});

  static Future<bool> isABDevice() async =>
      await _channel.invokeMethod('isABDevice');

  static Future<void> setPerformanceMode(bool enable) async =>
      await _channel.invokeMethod('setPerformanceMode', {'enable': enable});

  static Future<bool> getPerformanceMode() async =>
      await _channel.invokeMethod('getPerformanceMode');

  static Future<void> startActivity({String pkg, String cls}) async =>
      await _channel.invokeMethod('startActivity', {'pkg': pkg, 'cls': cls});

  static Future<int> getAccentColor() async =>
      await _channel.invokeMethod('getAccentColor');

  static UpdateStatus _strToStatusEnum(String value) =>
      UpdateStatus.values.firstWhere((e) =>
          e.toString().split('.')[1].toUpperCase() == value.toUpperCase());
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
  INSTALLATION_CANCELLED,
}

class Persistent {
  static const int UNKNOWN = 0;
  static const int INCOMPLETE = 1;
  static const int VERIFIED = 2;
}
