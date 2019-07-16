package co.potatoproject.androidflutterupdater;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.icu.text.DateFormat;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;
import android.text.format.Formatter;
import android.util.Log;

import org.json.JSONException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import co.potatoproject.androidflutterupdater.controller.UpdaterController;
import co.potatoproject.androidflutterupdater.controller.UpdaterService;
import co.potatoproject.androidflutterupdater.download.DownloadClient;
import co.potatoproject.androidflutterupdater.misc.Constants;
import co.potatoproject.androidflutterupdater.misc.StringGenerator;
import co.potatoproject.androidflutterupdater.misc.Utils;
import co.potatoproject.androidflutterupdater.model.UpdateInfo;
import co.potatoproject.androidflutterupdater.model.UpdateStatus;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.EventChannel.StreamHandler;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.PluginRegistry.Registrar;

/**
 * AndroidFlutterUpdaterPlugin
 */
@SuppressWarnings("Convert2Lambda")
public class AndroidFlutterUpdaterPlugin {

    private final Activity mActivity;
    private static final String TAG = "AndroidFlutterUpdaterPlugin";
    private UpdaterService mUpdaterService;
    private UpdaterController mUpdaterController;
    private List<String> mUpdateIds;
    private BroadcastReceiver mBroadcastReceiver;
    private final NativeStreamHandler mNativeStreamHandler = new NativeStreamHandler();

    class NativeStreamHandler implements StreamHandler {

        EventChannel.EventSink eventSink;

        @Override
        public void onListen(Object o, EventChannel.EventSink eventSink) {
            this.eventSink = eventSink;
        }

        void emitData(Object data) {
            if (eventSink != null && mActivity != null) {
                mActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        eventSink.success(data);
                    }
                });
            }
        }

        @Override
        public void onCancel(Object o) {

        }
    }

    private AndroidFlutterUpdaterPlugin(MethodChannel methodProvider, EventChannel eventProvider, Activity activity) {
        this.mActivity = activity;
        mBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String downloadId = intent.getStringExtra(UpdaterController.EXTRA_DOWNLOAD_ID);
                UpdateInfo update = mUpdaterController.getUpdate(downloadId);

                if (UpdaterController.ACTION_UPDATE_REMOVED.equals(intent.getAction()) || update == null) {
                    mUpdateIds.remove(downloadId);
                    mNativeStreamHandler.emitData(downloadId + '~' +
                            (update == null ? Integer.toString(-1) : update.getProgress()));
                } else if (UpdaterController.ACTION_UPDATE_STATUS.equals(intent.getAction())) {
                    mNativeStreamHandler.emitData(downloadId + '~' + update.getProgress());
                } else if (UpdaterController.ACTION_DOWNLOAD_PROGRESS.equals(intent.getAction()) ||
                        UpdaterController.ACTION_INSTALL_PROGRESS.equals(intent.getAction())) {
                    mNativeStreamHandler.emitData(downloadId + '~' + update.getProgress());
                }
            }
        };

        serviceBind();

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(UpdaterController.ACTION_UPDATE_STATUS);
        intentFilter.addAction(UpdaterController.ACTION_DOWNLOAD_PROGRESS);
        intentFilter.addAction(UpdaterController.ACTION_INSTALL_PROGRESS);
        intentFilter.addAction(UpdaterController.ACTION_UPDATE_REMOVED);
        LocalBroadcastManager.getInstance(activity).registerReceiver(mBroadcastReceiver, intentFilter);

        methodProvider.setMethodCallHandler(new MethodChannel.MethodCallHandler() {
            @Override
            public void onMethodCall(@NonNull MethodCall methodCall, @NonNull MethodChannel.Result result) {
                switch (methodCall.method) {
                    case "getPlatformVersion":
                        resultSuccess(result, "Android " + android.os.Build.VERSION.RELEASE);
                        break;
                    case "serviceUnbind":
                        serviceUnbind();
                        resultSuccess(result, null);
                        break;
                    case "serviceBind":
                        serviceBind();
                        resultSuccess(result, null);
                        break;
                    case "checkForUpdates":
                        getUpdatesList(true);
                        resultSuccess(result, null);
                        break;
                    case "getLastChecked":
                        resultSuccess(result, getLastCheckedString());
                        break;
                    case "getDownloads":
                        resultSuccess(result, mUpdateIds);
                        break;
                    case "startDownload": {
                        final String id = methodCall.argument("id");
                        startDownload(id);
                        resultSuccess(result, null);
                        break;
                    }
                    case "cancelAndDelete": {
                        final String id = methodCall.argument("id");
                        cancelAndDeleteDownload(id);
                        resultSuccess(result, null);
                        break;
                    }
                    case "pauseDownload": {
                        final String id = methodCall.argument("id");
                        pauseDownload(id);
                        resultSuccess(result, null);
                        break;
                    }
                    case "resumeDownload": {
                        final String id = methodCall.argument("id");
                        resumeDownload(id);
                        resultSuccess(result, null);
                        break;
                    }
                    case "verifyDownload": {
                        final String id = methodCall.argument("id");
                        verifyDownload(id);
                        resultSuccess(result, null);
                        break;
                    }
                    case "startUpdate": {
                        final String id = methodCall.argument("id");
                        startUpdate(id);
                        resultSuccess(result, null);
                        break;
                    }
                    case "needsWarn": {
                        resultSuccess(result, needsWarn());
                        break;
                    }
                    case "getWarn": {
                        resultSuccess(result, getWarn());
                        break;
                    }
                    case "setWarn": {
                        final Boolean enable = methodCall.argument("enable");
                        setWarn(enable == null ? false : enable);
                        resultSuccess(result, null);
                        break;
                    }
                    case "getAutoDelete": {
                        resultSuccess(result, getAutoDelete());
                        break;
                    }
                    case "setAutoDelete": {
                        final Boolean enable = methodCall.argument("enable");
                        setAutoDelete(enable == null ? false : enable);
                        resultSuccess(result, null);
                        break;
                    }
                    case "getName": {
                        final String id = methodCall.argument("id");
                        resultSuccess(result, getName(id));
                        break;
                    }
                    case "getVersion": {
                        final String id = methodCall.argument("id");
                        resultSuccess(result, getVersion(id));
                        break;
                    }
                    case "getTimestamp": {
                        final String id = methodCall.argument("id");
                        resultSuccess(result, getTimestampString(id));
                        break;
                    }
                    case "getDeviceName":
                        resultSuccess(result, Utils.getDevice());
                        break;
                    case "getModel":
                        resultSuccess(result, Utils.getModel());
                        break;
                    case "getBuildVersion":
                        resultSuccess(result, Utils.getBuildVersion());
                        break;
                    case "getBuildDate":
                        resultSuccess(result, Utils.getBuildDate(mActivity));
                        break;
                    case "getReleaseType":
                        resultSuccess(result, Utils.getReleaseType(mActivity));
                        break;
                    case "setReleaseType": {
                        final String type = methodCall.argument("type");
                        Utils.setReleaseType(mActivity, type);
                        getUpdatesList(true);
                        resultSuccess(result, null);
                        break;
                    }
                    case "getVerify":
                        resultSuccess(result, Utils.getVerify(mActivity));
                        break;
                    case "setVerify": {
                        final Boolean enable = methodCall.argument("enable");
                        Utils.setVerify(mActivity, enable == null ? true : enable);
                        resultSuccess(result, null);
                        break;
                    }
                    case "getProp": {
                        final String prop = methodCall.argument("prop");
                        resultSuccess(result, Utils.getProp(prop));
                        break;
                    }
                    case "installUpdate": {
                        final String id = methodCall.argument("id");
                        final boolean canInstall = Utils.canInstall(mUpdaterController.getUpdate(id));
                        if (canInstall)
                            Utils.triggerUpdate(mActivity, id);
                        resultSuccess(result, canInstall);
                        break;
                    }
                    case "getUpdateCheckInterval":
                        resultSuccess(result, Utils.getUpdateCheckSetting(mActivity));
                        break;
                    case "setUpdateCheckInterval": {
                        final Integer interval = methodCall.argument("interval");
                        Utils.setUpdateCheckSetting(mActivity, interval == null
                                ? Constants.AUTO_UPDATES_CHECK_INTERVAL_WEEKLY
                                : interval);
                        resultSuccess(result, null);
                        break;
                    }
                    case "isABDevice":
                        resultSuccess(result, Utils.isABDevice());
                        break;
                    case "setPerformanceMode": {
                        final Boolean enable = methodCall.argument("enable");
                        Utils.setPerformanceMode(mActivity, mUpdaterController, enable == null ? false : enable);
                        resultSuccess(result, null);
                        break;
                    }
                    case "getPerformanceMode":
                        resultSuccess(result, Utils.getPerformanceMode(mActivity));
                        break;
                    case "getDownloadProgress": {
                        final String id = methodCall.argument("id");
                        resultSuccess(result, mUpdaterController.getUpdate(id).getProgress());
                        break;
                    }
                    case "getInstallProgress": {
                        final String id = methodCall.argument("id");
                        resultSuccess(result, mUpdaterController.getUpdate(id).getInstallProgress());
                        break;
                    }
                    case "getStatus": {
                        final String id = methodCall.argument("id");
                        resultSuccess(result, mUpdaterController.getUpdate(id).getStatus().toString());
                        break;
                    }
                    case "getPersistentStatus": {
                        final String id = methodCall.argument("id");
                        resultSuccess(result, mUpdaterController.getUpdate(id).getPersistentStatus());
                        break;
                    }
                    case "getEta": {
                        final String id = methodCall.argument("id");
                        resultSuccess(result, StringGenerator.formatETA(mActivity,
                                mUpdaterController.getUpdate(id).getEta() * 1000));
                        break;
                    }
                    case "getSpeed": {
                        final String id = methodCall.argument("id");
                        resultSuccess(result, Formatter.formatFileSize(mActivity,
                                mUpdaterController.getUpdate(id).getSpeed()));
                        break;
                    }
                    case "getSize": {
                        final String id = methodCall.argument("id");
                        resultSuccess(result, Long.toString(mUpdaterController.getUpdate(id).getFileSize()));
                        break;
                    }
                    case "startActivity": {
                        final String pkg = methodCall.argument("pkg");
                        final String cls = methodCall.argument("cls");
                        if (pkg != null && cls != null) {
                            mActivity.startActivity(new Intent().setComponent(new ComponentName(
                                    pkg, cls)));
                        }
                        result.success(null);
                        break;
                    }
                    case "getAccentColor": {
                        result.success(getAccentColor());
                        break;
                    }
                    default:
                        result.notImplemented();
                        break;
                }
            }
        });

        eventProvider.setStreamHandler(mNativeStreamHandler);
    }

    private void resultSuccess(MethodChannel.Result result, Object object) {
        if (mActivity == null) return;
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                result.success(object);
            }
        });
    }

    public static void registerWith(Registrar registrar) {
        final MethodChannel methodProvider = new MethodChannel(registrar.messenger(), "android_flutter_updater/methods");
        final EventChannel eventProvider = new EventChannel(registrar.view(), "android_flutter_updater/events");
        new AndroidFlutterUpdaterPlugin(methodProvider, eventProvider, registrar.activity());
    }

    private void serviceUnbind() {
        LocalBroadcastManager.getInstance(mActivity).unregisterReceiver(mBroadcastReceiver);
        if (mUpdaterService != null)
            mActivity.unbindService(mConnection);
    }

    private void serviceBind() {
        Intent intent = new Intent(mActivity, UpdaterService.class);
        mActivity.startService(intent);
        mActivity.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @SuppressLint("InlinedApi")
    private String getLastCheckedString() {
        if (mActivity == null) return "¯\\_(ツ)_/¯";
        final SharedPreferences preferences =
                PreferenceManager.getDefaultSharedPreferences(mActivity);
        long lastCheck = preferences.getLong(Constants.PREF_LAST_UPDATE_CHECK, -1) / 1000;
        return String.format(Locale.ROOT, mActivity.getResources().getString(R.string.header_last_updates_check),
                StringGenerator.getDateLocalized(mActivity, DateFormat.LONG, lastCheck),
                StringGenerator.getTimeLocalized(mActivity, lastCheck));
    }

    private void getUpdatesList(boolean ignoreCache) {
        File jsonFile = Utils.getCachedUpdateList(mActivity);
        if (jsonFile.exists() && !ignoreCache) {
            try {
                loadUpdatesList(jsonFile);
                Log.d(TAG, "Cached list parsed");
            } catch (IOException | JSONException e) {
                Log.e(TAG, "Error while parsing json list", e);
            }
        } else {
            downloadUpdatesList();
        }
    }

    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            UpdaterService.LocalBinder binder = (UpdaterService.LocalBinder) service;
            mUpdaterService = binder.getService();
            mUpdaterController = mUpdaterService.getUpdaterController();
            getUpdatesList(false);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mUpdaterService = null;
        }
    };

    private void downloadUpdatesList() {
        final File jsonFile = Utils.getCachedUpdateList(mActivity);
        final File jsonFileTmp = new File(jsonFile.getAbsolutePath() + UUID.randomUUID());
        String url = Utils.getServerURL(mActivity);
        Log.d(TAG, "Checking " + url);

        DownloadClient.DownloadCallback callback = new DownloadClient.DownloadCallback() {
            @Override
            public void onFailure(final boolean cancelled) {
                Log.e(TAG, "Could not download updates list");
            }

            @Override
            public void onResponse(int statusCode, String url,
                                   DownloadClient.Headers headers) {
                Log.d(TAG, "Updates list response obtained");
            }

            @Override
            public void onSuccess(File destination) {
                processNewJson(jsonFile, jsonFileTmp);
            }
        };

        final DownloadClient downloadClient;
        try {
            downloadClient = new DownloadClient.Builder()
                    .setUrl(url)
                    .setDestination(jsonFileTmp)
                    .setDownloadCallback(callback)
                    .build();
        } catch (IOException exception) {
            Log.e(TAG, "Could not build download client");
            return;
        }

        downloadClient.start();
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void processNewJson(File json, File jsonNew) {
        try {
            loadUpdatesList(jsonNew);
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(mActivity);
            long millis = System.currentTimeMillis();
            preferences.edit().putLong(Constants.PREF_LAST_UPDATE_CHECK, millis).apply();
            if (json.exists() && Utils.isUpdateCheckEnabled(mActivity) &&
                    Utils.checkForNewUpdates(json, jsonNew, mActivity)) {
                UpdatesCheckReceiver.updateRepeatingUpdatesCheck(mActivity);
            }
            // In case we set a one-shot check because of a previous failure
            UpdatesCheckReceiver.cancelUpdatesCheck(mActivity);
            jsonNew.renameTo(json);
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Could not read json", e);
        }
    }

    private void loadUpdatesList(File jsonFile)
            throws IOException, JSONException {
        Log.d(TAG, "Adding remote updates");
        UpdaterController controller = mUpdaterService.getUpdaterController();

        List<UpdateInfo> updates = Utils.parseJson(jsonFile, true, mActivity);
        List<String> updatesOnline = new ArrayList<>();
        for (UpdateInfo update : updates) {
            /*newUpdates |= */
            controller.addUpdate(update);
            updatesOnline.add(update.getDownloadId());
        }
        controller.setUpdatesAvailableOnline(updatesOnline, true);

        List<String> updateIds = new ArrayList<>();
        List<UpdateInfo> sortedUpdates = controller.getUpdates();
        if (!sortedUpdates.isEmpty()) {
            boolean updatesAvailable = false;
            sortedUpdates.sort(new Comparator<UpdateInfo>() {
                @Override
                public int compare(UpdateInfo u1, UpdateInfo u2) {
                    return Long.compare(u2.getTimestamp(), u1.getTimestamp());
                }
            });
            for (UpdateInfo update : sortedUpdates) {
                if (Utils.canInstall(update))
                    updatesAvailable = true;
                updateIds.add(update.getDownloadId());
            }
            mNativeStreamHandler.emitData("update_available~" + updatesAvailable);
        } else {
            mNativeStreamHandler.emitData("update_available~false");
        }
        mUpdateIds = updateIds;
    }

    private void cancelAndDeleteDownload(String downloadId) {
        pauseDownload(downloadId);
        mUpdaterController.deleteUpdate(downloadId);
        mNativeStreamHandler.emitData(downloadId + '~' + 0);
    }

    private void pauseDownload(String downloadId) {
        UpdateInfo update = mUpdaterController.getUpdate(downloadId);
        mUpdaterController.pauseDownload(downloadId);
        mNativeStreamHandler.emitData(downloadId + '~' + update.getProgress());
    }

    private void resumeDownload(String downloadId) {
        UpdateInfo update = mUpdaterController.getUpdate(downloadId);
        mUpdaterController.resumeDownload(downloadId);
        mNativeStreamHandler.emitData(downloadId + '~' + update.getProgress());
    }

    private void verifyDownload(String downloadId) {
        UpdateInfo update = mUpdaterController.getUpdate(downloadId);
        if (update.getStatus() == UpdateStatus.DOWNLOADED ||
                update.getStatus() == UpdateStatus.VERIFICATION_FAILED)
            resumeDownload(downloadId);
        mNativeStreamHandler.emitData(downloadId + '~' + update.getProgress());
    }

    private String getName(String downloadId) {
        UpdateInfo update = mUpdaterController.getUpdate(downloadId);
        return update.getName();
    }

    private String getTimestampString(String downloadId) {
        UpdateInfo update = mUpdaterController.getUpdate(downloadId);
        return StringGenerator.getDateLocalizedUTC(mActivity, java.text.DateFormat.LONG, update.getTimestamp());
    }

    private String getVersion(String downloadId) {
        UpdateInfo update = mUpdaterController.getUpdate(downloadId);
        return update.getVersion();
    }

    private void startDownload(String updateId) {
        mUpdaterController.startDownload(updateId);
    }

    private void startUpdate(String updateId) {
        Utils.triggerUpdate(mActivity, updateId);
    }

    private boolean needsWarn() {
        SharedPreferences preferences =
                PreferenceManager.getDefaultSharedPreferences(mActivity);
        return !Utils.isOnWifiOrEthernet(mActivity) &&
                preferences.getBoolean(Constants.PREF_MOBILE_DATA_WARNING, true);
    }

    private boolean getWarn() {
        SharedPreferences preferences =
                PreferenceManager.getDefaultSharedPreferences(mActivity);
        return preferences.getBoolean(Constants.PREF_MOBILE_DATA_WARNING, true);
    }

    private void setWarn(boolean enable) {
        SharedPreferences preferences =
                PreferenceManager.getDefaultSharedPreferences(mActivity);
        preferences.edit().putBoolean(Constants.PREF_MOBILE_DATA_WARNING, enable).apply();
    }

    private boolean getAutoDelete() {
        SharedPreferences preferences =
                PreferenceManager.getDefaultSharedPreferences(mActivity);
        return preferences.getBoolean(Constants.PREF_AUTO_DELETE_UPDATES, true);
    }

    private void setAutoDelete(boolean enable) {
        SharedPreferences preferences =
                PreferenceManager.getDefaultSharedPreferences(mActivity);
        preferences.edit().putBoolean(Constants.PREF_AUTO_DELETE_UPDATES, enable).apply();
    }

    private int getAccentColor() {
        String colResName = "accent_device_default_dark";
        Resources res = null;
        try {
            res = mActivity.getPackageManager().getResourcesForApplication("android");
            int resId = res.getIdentifier("android:color/" + colResName, null, null);
            return res.getColor(resId);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return 0;
    }
}
