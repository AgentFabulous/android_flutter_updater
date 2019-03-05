
# android_flutter_updater

A Flutter plugin to allow updating Android. Based on the OTA Updater by LineageOS.

## Getting Started
- Make a new Flutter app and add the following to dependencies in your `pubspec.yaml`.
```
  android_flutter_updater:  
    git:
      url: git://github.com/AgentFabulous/android_flutter_updater
```
- Open your .gitignore file and add:
```**/android/libs```
- Open `android/build.gradle` and add the following to your `allProjects` block:
```
    afterEvaluate {
        if (plugins.hasPlugin('android') ||
                plugins.hasPlugin('com.android.application') ||
                plugins.hasPlugin('com.android.library')) {
            dependencies {
                compileOnly fileTree(dir: 'libs/', include: ['*.jar'])
            }
        }
    }
```
- Create a new directory under `android ` called `libs ` and add up your project's `framework.jar` to `android/libs`.
This is necessary because this app accesses many hiddenapi of Android which aren't available on the default SDK jars.
- Open `android/app/build.gradle` and change `minSdkVersion` to `24`.
- Add the following permissions to your `android/app/src/main/AndroidManifest.xml`:
```
    <uses-permission android:name="android.permission.ACCESS_CACHE_FILESYSTEM" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.REBOOT" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission android:name="android.permission.RECOVERY" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
```
- Add the following lines to the `application` tag of your `android/app/src/main/AndroidManifest.xml`:
```
        <service android:name="co.potatoproject.androidflutterupdater.controller.UpdaterService" />
        <service android:name="co.potatoproject.androidflutterupdater.ExportUpdateService" />
        <receiver android:name="co.potatoproject.androidflutterupdater.UpdaterReceiver" android:exported="false">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED"/>
            </intent-filter>
        </receiver>
        <receiver android:name="co.potatoproject.androidflutterupdater.UpdatesCheckReceiver">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED"/>
            </intent-filter>
        </receiver>
```
  
### That's it!
You can now simply do:
```import 'package:android_flutter_updater/android_flutter_updater.dart';```

An example app can be found [here](https://github.com/AgentFabulous/PotatoCenter).

## Device setup
The plugin reads numerous device props to be able to work with different implementations and OTA-APIs.
- Firstly, set your project name to `ro.build.project`. All other props will depend on the value of this prop.
Example: ```PRODUCT_PROPERTY_OVERRIDES += ro.build.project=potato```
The value of this prop will be used in place of `{project}` this point on.
- `ro.{project}.version` - Set this to your project's version string.
- `ro.{project}.device` - Set this to your target device's codename.
- `ro.{project}.ota_path` - Set this to the path you'd want to download files to. This path is relative to the default external storage. So, setting this to `PotatoCenter` would normally mean `/sdcard/PotatoCenter`.
- `ro.{project}.type` - Set this to your build's release type. Can also be thought of as an OTA Channel (weekly, nightly, beta, etc).
- `{project}.updater.uri` - Set this to your Server/API URL. `{device}` and `{type}` would be replaced by the current device and release-type/build-channel by the plugin as can be seen in the [method](https://github.com/AgentFabulous/android_flutter_updater/blob/8aea74a45668dcb19cf0bd5231dc704f6d94f1e6/android/src/main/java/co/potatoproject/androidflutterupdater/misc/Utils.java#L152-L162) and [fallback/default value](https://github.com/AgentFabulous/android_flutter_updater/blob/8aea74a45668dcb19cf0bd5231dc704f6d94f1e6/android/src/main/res/values/strings.xml#L30).

So, for [POSP](https://github.com/PotatoProject), it would look something like so:
```
PRODUCT_PROPERTY_OVERRIDES += \
    ro.build.project=potato \
    ro.potato.version=2.2 \
    ro.potato.device=beryllium \
    ...
```
Of course, one would not hardcode values like version and device code-name to props directly. This is solely for demonstration purposes. 

You can find the full example [here](https://github.com/PotatoProject/vendor_potato/blob/baked-release/config/common.mk).

**NOTE**: `potato.updater.uri` and `ro.potato.ota_path` props are not defined here because they are same as the plugin's fallback values and don't need to be overriden. If you plan on using the plugin, you WILL need to set these props.
