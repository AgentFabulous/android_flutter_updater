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
import android.icu.text.DateFormat;
import android.icu.text.NumberFormat;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.text.format.Formatter;
import android.util.Log;

import org.json.JSONException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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
    private final ProgressStreamHandler mProgressStreamHandler = new ProgressStreamHandler();

    @SuppressWarnings("unchecked")
    private HashMap<String, String> mDataMap = new HashMap<String, String>() {{
        put("has_active_downloads", "false");
        put("last_checked", getLastCheckedString());
        put("percentage", "0%");
        put("size", "0");
        put("eta", "");
        put("speed", "");
        put("force_update_ui_ui", "false");
        put("update_available", "false");
        put("update_status", UpdateStatus.UNKNOWN.toString());
    }};


    class ProgressStreamHandler implements StreamHandler {

        EventChannel.EventSink eventSink;

        @Override
        public void onListen(Object o, EventChannel.EventSink eventSink) {
            this.eventSink = eventSink;
        }

        void emitData(Object data) {
            if (eventSink != null)
                eventSink.success(data);
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
                    if (mUpdateIds.isEmpty())
                        mDataMap.put("update_available", "false");
                    mDataMap.put("force_update_ui", "true");
                    mProgressStreamHandler.emitData(mDataMap);
                    mDataMap.put("force_update_ui", "false");
                } else if (UpdaterController.ACTION_UPDATE_STATUS.equals(intent.getAction())) {
                    String percentage = NumberFormat.getPercentInstance().format(
                            update.getProgress() / 100.f);
                    String speed = Formatter.formatFileSize(mActivity, update.getSpeed());
                    CharSequence eta = StringGenerator.formatETA(mActivity, update.getEta() * 1000);
                    mDataMap.put("percentage", percentage);
                    mDataMap.put("size", Long.toString(update.getFileSize()));
                    mDataMap.put("eta", eta.toString());
                    mDataMap.put("speed", speed);
                    mDataMap.put("update_status", update.getStatus().toString());
                    mProgressStreamHandler.emitData(mDataMap);
                } else if (UpdaterController.ACTION_DOWNLOAD_PROGRESS.equals(intent.getAction()) ||
                        UpdaterController.ACTION_INSTALL_PROGRESS.equals(intent.getAction())) {
                    String percentage = NumberFormat.getPercentInstance().format(
                            update.getProgress() / 100.f);
                    String speed = Formatter.formatFileSize(mActivity, update.getSpeed());
                    CharSequence eta = StringGenerator.formatETA(mActivity, update.getEta() * 1000);
                    mDataMap.put("has_active_downloads", Boolean.toString(mUpdaterController.hasActiveDownloads()));
                    mDataMap.put("percentage", percentage);
                    mDataMap.put("size", Long.toString(update.getFileSize()));
                    mDataMap.put("eta", eta.toString());
                    mDataMap.put("speed", speed);
                    mDataMap.put("update_status", update.getStatus().toString());
                    mProgressStreamHandler.emitData(mDataMap);
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
            public void onMethodCall(MethodCall methodCall, MethodChannel.Result result) {
                switch (methodCall.method) {
                    case "getPlatformVersion":
                        result.success("Android " + android.os.Build.VERSION.RELEASE);
                        break;
                    case "serviceUnbind":
                        serviceUnbind();
                        result.success(null);
                        break;
                    case "serviceBind":
                        serviceBind();
                        result.success(null);
                        break;
                    case "checkForUpdates":
                        getUpdatesList(true);
                        result.success(null);
                        break;
                    case "getLastChecked":
                        result.success(getLastCheckedString());
                        break;
                    case "getDownloads":
                        result.success(mUpdateIds);
                        break;
                    case "startDownload": {
                        final String id = methodCall.argument("id");
                        startDownload(id);
                        result.success(null);
                        break;
                    }
                    case "cancelAndDelete": {
                        final String id = methodCall.argument("id");
                        cancelAndDeleteDownload(id);
                        result.success(null);
                        break;
                    }
                    case "pauseDownload": {
                        final String id = methodCall.argument("id");
                        pauseDownload(id);
                        result.success(null);
                        break;
                    }
                    case "resumeDownload": {
                        final String id = methodCall.argument("id");
                        resumeDownload(id);
                        result.success(null);
                        break;
                    }
                    case "verifyDownload": {
                        final String id = methodCall.argument("id");
                        verifyDownload(id);
                        result.success(null);
                        break;
                    }
                    case "startUpdate": {
                        final String id = methodCall.argument("id");
                        startUpdate(id);
                        result.success(null);
                        break;
                    }
                    case "needsWarn": {
                        result.success(needsWarn());
                        break;
                    }
                    case "getWarn": {
                        result.success(getWarn());
                        break;
                    }
                    case "setWarn": {
                        final Boolean enable = methodCall.argument("enable");
                        setWarn(enable == null ? false : enable);
                        result.success(null);
                        break;
                    }
                    case "getAutoDelete": {
                        result.success(getAutoDelete());
                        break;
                    }
                    case "setAutoDelete": {
                        final Boolean enable = methodCall.argument("enable");
                        setAutoDelete(enable == null ? false : enable);
                        result.success(null);
                        break;
                    }
                    case "getName": {
                        final String id = methodCall.argument("id");
                        result.success(getName(id));
                        break;
                    }
                    case "getVersion": {
                        final String id = methodCall.argument("id");
                        result.success(getVersion(id));
                        break;
                    }
                    case "getTimestamp": {
                        final String id = methodCall.argument("id");
                        result.success(getTimestampString(id));
                        break;
                    }
                    case "getDeviceName":
                        result.success(Utils.getDevice());
                        break;
                    case "getModel":
                        result.success(Utils.getModel());
                        break;
                    case "getBuildVersion":
                        result.success(Utils.getBuildVersion());
                        break;
                    case "getBuildDate":
                        result.success(Utils.getBuildDate(mActivity));
                        break;
                    case "getReleaseType":
                        result.success(Utils.getReleaseType(mActivity));
                        break;
                    case "setReleaseType": {
                        final String type = methodCall.argument("type");
                        Utils.setReleaseType(mActivity, type);
                        getUpdatesList(true);
                        result.success(null);
                        break;
                    }
                    case "getVerify":
                        result.success(Utils.getVerify(mActivity));
                        break;
                    case "setVerify": {
                        final Boolean enable = methodCall.argument("enable");
                        Utils.setVerify(mActivity, enable == null ? true : enable);
                        result.success(null);
                        break;
                    }
                    case "getNativeStatus":
                        result.success(mDataMap);
                        break;
                    case "getProp": {
                        final String prop = methodCall.argument("prop");
                        result.success(Utils.getProp(prop));
                        break;
                    }
                    case "installUpdate": {
                        final String id = methodCall.argument("id");
                        final boolean canInstall = Utils.canInstall(mUpdaterController.getUpdate(id));
                        if (canInstall)
                            Utils.triggerUpdate(mActivity, id);
                        result.success(canInstall);
                        break;
                    }
                    case "getUpdateCheckInterval":
                        result.success(Utils.getUpdateCheckSetting(mActivity));
                        break;
                    case "setUpdateCheckInterval": {
                        final Integer interval = methodCall.argument("interval");
                        Utils.setUpdateCheckSetting(mActivity, interval == null
                                ? Constants.AUTO_UPDATES_CHECK_INTERVAL_WEEKLY
                                : interval);
                        result.success(null);
                        break;
                    }
                    case "isABDevice":
                        result.success(Utils.isABDevice());
                        break;
                    case "setPerformanceMode": {
                        final Boolean enable = methodCall.argument("enable");
                        Utils.setPerformanceMode(mActivity, mUpdaterController, enable == null ? false : enable);
                        result.success(null);
                        break;
                    }
                    case "getPerformanceMode":
                        result.success(Utils.getPerformanceMode(mActivity));
                        break;
                    default:
                        result.notImplemented();
                        break;
                }
            }
        });

        eventProvider.setStreamHandler(mProgressStreamHandler);
    }

    public static void registerWith(Registrar registrar) {
        final MethodChannel methodProvider = new MethodChannel(registrar.messenger(), "android_flutter_updater_methods");
        final EventChannel eventProvider = new EventChannel(registrar.view(), "android_flutter_updater_events");
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
        String ret = String.format(Locale.ROOT, mActivity.getResources().getString(R.string.header_last_updates_check),
                StringGenerator.getDateLocalized(mActivity, DateFormat.LONG, lastCheck),
                StringGenerator.getTimeLocalized(mActivity, lastCheck));
        mDataMap.put("last_checked", ret);
        mProgressStreamHandler.emitData(mDataMap);
        return ret;
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
        //boolean newUpdates = false;

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
            mDataMap.put("update_available", "true");
            sortedUpdates.sort(new Comparator<UpdateInfo>() {
                @Override
                public int compare(UpdateInfo u1, UpdateInfo u2) {
                    return Long.compare(u2.getTimestamp(), u1.getTimestamp());
                }
            });
            for (UpdateInfo update : sortedUpdates)
                updateIds.add(update.getDownloadId());
        } else {
            mDataMap.put("update_available", "false");
        }

        mDataMap.put("force_update_ui", "true");
        mProgressStreamHandler.emitData(mDataMap);
        mDataMap.put("force_update_ui", "false");

        mUpdateIds = updateIds;
    }

    private void cancelAndDeleteDownload(String downloadId) {
        pauseDownload(downloadId);
        mDataMap.put("has_active_downloads", Boolean.toString(mUpdaterController.hasActiveDownloads()));
        mProgressStreamHandler.emitData(mDataMap);
        mUpdaterController.deleteUpdate(downloadId);
    }

    private void pauseDownload(String downloadId) {
        UpdateInfo update = mUpdaterController.getUpdate(downloadId);
        mDataMap.put("update_status", update.getStatus().toString());
        mProgressStreamHandler.emitData(mDataMap);
        mUpdaterController.pauseDownload(downloadId);
    }

    private void resumeDownload(String downloadId) {
        UpdateInfo update = mUpdaterController.getUpdate(downloadId);
        mDataMap.put("update_status", update.getStatus().toString());
        mProgressStreamHandler.emitData(mDataMap);
        mUpdaterController.resumeDownload(downloadId);
    }

    private void verifyDownload(String downloadId) {
        UpdateInfo update = mUpdaterController.getUpdate(downloadId);
        if (update.getStatus() == UpdateStatus.DOWNLOADED ||
                update.getStatus() == UpdateStatus.VERIFICATION_FAILED)
            resumeDownload(downloadId);
    }

    private String getName(String downloadId) {
        UpdateInfo update = mUpdaterController.getUpdate(downloadId);
        return update.getName();
    }

    private String getTimestampString(String downloadId) {
        UpdateInfo update = mUpdaterController.getUpdate(downloadId);
        return StringGenerator.getDateLocalizedUTC(mActivity,
                DateFormat.MEDIUM, update.getTimestamp());
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
}
