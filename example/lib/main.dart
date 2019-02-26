import 'dart:async';

import 'package:android_flutter_updater/android_flutter_updater.dart';
import 'package:flutter/material.dart';

void main() => runApp(MyApp());

class AppData {
  static final AppData _singleton = new AppData._internal();

  factory AppData() => _singleton;

  AppData._internal();

  Map nativeData = new Map();
}

class MyApp extends StatefulWidget {
  @override
  _MyAppState createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  String _lastChecked = 'Unknown';
  List<String> updateIds = new List();
  StreamSubscription s;

  @override
  void initState() {
    super.initState();
    stuff();
    AndroidFlutterUpdater.registerStreamListener(
        subscription: s, fn: updateSingleton);
    AndroidFlutterUpdater.getNativeStatus().then((map) {
      AppData().nativeData = map;
    });
  }

  void updateSingleton(dynamic nativeMap) {
    setState(() {
      AppData().nativeData = nativeMap;
      print(AppData().nativeData['update_status'].toString());
    });
  }

  void stuff() {
    AndroidFlutterUpdater.serviceBind()
        .then((v) => AndroidFlutterUpdater.getLastChecked().then((v) {
              if (!mounted) return;
              setState(() => _lastChecked = v);
            }));
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        floatingActionButton:
            FloatingActionButton(onPressed: stuff, child: Icon(Icons.add)),
        appBar: AppBar(
          title: const Text('Plugin example app'),
        ),
        body: Center(
          child: Padding(
            padding: const EdgeInsets.all(16.0),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: <Widget>[
                Text('$_lastChecked\n'),
                RaisedButton(
                  onPressed: () => AndroidFlutterUpdater.checkForUpdates().then(
                      (v) => AndroidFlutterUpdater.getLastChecked().then((v) {
                            AndroidFlutterUpdater.getDownloads()
                                .then((v) => updateIds = v);
                            if (!mounted) return;
                            setState(() {
                              _lastChecked = v;
                            });
                          })),
                  child: Text("Check for Updates"),
                ),
                Flexible(
                  child: ListView.builder(
                      itemCount: updateIds == null ? 0 : updateIds.length,
                      itemBuilder: (context, index) {
                        return Row(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: <Widget>[
                            Text("${updateIds[index]}"),
                            strToBool(AppData()
                                        .nativeData['has_active_downloads']) ||
                                    enumFromString(AppData()
                                            .nativeData['update_status']
                                            .toString()) ==
                                        UpdateStatus.STARTING
                                ? Container()
                                : IconButton(
                                    onPressed: () =>
                                        AndroidFlutterUpdater.startDownload(
                                            updateIds[index]),
                                    icon: Icon(Icons.file_download)),
                            enumFromString(AppData().nativeData['update_status'].toString()) !=
                                        UpdateStatus.DOWNLOADING &&
                                    enumFromString(AppData().nativeData['update_status'].toString()) !=
                                        UpdateStatus.PAUSED &&
                                    enumFromString(AppData().nativeData['update_status'].toString()) !=
                                        UpdateStatus.STARTING
                                ? Container()
                                : (enumFromString(AppData().nativeData['update_status'].toString()) ==
                                            UpdateStatus.DOWNLOADING ||
                                        enumFromString(AppData()
                                                .nativeData['update_status']
                                                .toString()) ==
                                            UpdateStatus.STARTING)
                                    ? IconButton(
                                        onPressed: () => AndroidFlutterUpdater.pauseDownload(
                                            updateIds[index]),
                                        icon: Icon(Icons.pause))
                                    : IconButton(
                                        onPressed: () =>
                                            AndroidFlutterUpdater.resumeDownload(
                                                updateIds[index]),
                                        icon: Icon(Icons.play_arrow)),
                            IconButton(
                                onPressed: () =>
                                    AndroidFlutterUpdater.cancelAndDelete(
                                        updateIds[index]),
                                icon: Icon(Icons.close)),
                            Text(AppData().nativeData['percentage'])
                          ],
                        );
                      }),
                )
              ],
            ),
          ),
        ),
      ),
    );
  }
}

bool strToBool(String ip) {
  return ip == null ? false : ip.toLowerCase() == "true";
}

dynamic enumFromString(String value) {
  return UpdateStatus.values.firstWhere(
      (e) => e.toString().split('.')[1].toUpperCase() == value.toUpperCase());
}
