/*
 * Copyright (C) 2017 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package co.potatoproject.androidflutterupdater.misc;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Environment;
import android.os.SystemProperties;
import android.os.storage.StorageManager;
import android.preference.PreferenceManager;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import co.potatoproject.androidflutterupdater.R;
import co.potatoproject.androidflutterupdater.UpdatesDbHelper;
import co.potatoproject.androidflutterupdater.controller.UpdaterController;
import co.potatoproject.androidflutterupdater.controller.UpdaterService;
import co.potatoproject.androidflutterupdater.model.Update;
import co.potatoproject.androidflutterupdater.model.UpdateBaseInfo;
import co.potatoproject.androidflutterupdater.model.UpdateInfo;

@SuppressWarnings("Convert2Lambda")
public class Utils {

    private static final String TAG = "Utils";

    private Utils() {
    }

    public static File getDownloadPath(Context context) {
        if (context == null)
            return null;
        String relativeDownloadPath =
                SystemProperties.get(getProjectProp(Constants.PROP_DOWNLOAD_PATH));
        if (relativeDownloadPath.trim().isEmpty())
            relativeDownloadPath = context.getString(R.string.download_path);
        File dir = new File(Environment.getExternalStorageDirectory(),
                relativeDownloadPath);
        if (!dir.isDirectory()) {
            if (dir.exists() || !dir.mkdirs()) {
                return null;
            }
        }
        return dir;
    }

    public static File getCachedUpdateList(Context context) {
        return new File(context.getCacheDir(), "updates.json");
    }

    // This should really return an UpdateBaseInfo object, but currently this only
    // used to initialize UpdateInfo objects
    private static UpdateInfo parseJsonUpdate(JSONObject object) throws JSONException {
        Update update = new Update();
        update.setTimestamp(object.getLong("datetime"));
        update.setName(object.getString("filename"));
        update.setDownloadId(object.getString("id"));
        update.setType(object.getString("romtype"));
        update.setFileSize(object.getLong("size"));
        update.setDownloadUrl(object.getString("url"));
        update.setVersion(object.getString("version"));
        return update;
    }

    private static boolean isCompatible(UpdateBaseInfo update, Context context) {
        if (!SystemProperties.getBoolean(getProjectProp(Constants.PROP_UPDATER_ALLOW_DOWNGRADING), false) &&
                update.getTimestamp() <= SystemProperties.getLong(Constants.PROP_BUILD_DATE, 0)) {
            Log.d(TAG, update.getName() + " is older than/equal to the current build");
            return false;
        }
        if (!update.getType().equalsIgnoreCase(getReleaseType(context))) {
            Log.d(TAG, update.getName() + " has type " + update.getType());
            return false;
        }
        return true;
    }

    public static boolean canInstall(UpdateBaseInfo update) {
        return (SystemProperties.getBoolean(getProjectProp(Constants.PROP_UPDATER_ALLOW_DOWNGRADING), false) ||
                update.getTimestamp() > SystemProperties.getLong(Constants.PROP_BUILD_DATE, 0)) &&
                update.getVersion().equalsIgnoreCase(
                        SystemProperties.get(getProjectProp(Constants.PROP_BUILD_VERSION)));
    }

    public static boolean isCurrent(UpdateBaseInfo update) {
        return update.getTimestamp() == SystemProperties.getLong(Constants.PROP_BUILD_DATE, 0);
    }

    public static List<UpdateInfo> parseJson(File file, boolean compatibleOnly, Context context)
            throws IOException, JSONException {
        List<UpdateInfo> updates = new ArrayList<>();

        StringBuilder json = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            for (String line; (line = br.readLine()) != null; ) {
                json.append(line);
            }
        }

        JSONObject obj = new JSONObject(json.toString());
        JSONArray updatesList = obj.getJSONArray("response");
        for (int i = 0; i < updatesList.length(); i++) {
            if (updatesList.isNull(i)) {
                continue;
            }
            try {
                UpdateInfo update = parseJsonUpdate(updatesList.getJSONObject(i));
                if (!compatibleOnly || isCompatible(update, context)) {
                    updates.add(update);
                } else {
                    Log.d(TAG, "Ignoring incompatible update " + update.getName());
                }
            } catch (JSONException e) {
                Log.e(TAG, "Could not parse update object, index=" + i, e);
            }
        }

        return updates;
    }

    public static String getServerURL(Context context) {
        String device = SystemProperties.get(getProjectProp(Constants.PROP_DEVICE));
        String type = getReleaseType(context);

        String serverUrl = SystemProperties.get(getProjectProp(Constants.PROP_UPDATER_URI));
        if (serverUrl.trim().isEmpty())
            serverUrl = context.getString(R.string.updater_server_url);

        return serverUrl.replace("{device}", device)
                .replace("{type}", type);
    }

    public static void triggerUpdate(Context context, String downloadId) {
        final Intent intent = new Intent(context, UpdaterService.class);
        intent.setAction(UpdaterService.ACTION_INSTALL_UPDATE);
        intent.putExtra(UpdaterService.EXTRA_DOWNLOAD_ID, downloadId);
        context.startService(intent);
    }

    @SuppressWarnings("deprecation")
    @SuppressLint("MissingPermission")
    public static boolean isNetworkAvailable(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(
                Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = cm.getActiveNetworkInfo();
        return !(info == null || !info.isConnected() || !info.isAvailable());
    }

    @SuppressWarnings("deprecation")
    public static boolean isOnWifiOrEthernet(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(
                Context.CONNECTIVITY_SERVICE);
        @SuppressLint("MissingPermission") NetworkInfo info = cm.getActiveNetworkInfo();
        return (info != null && (info.getType() == ConnectivityManager.TYPE_ETHERNET
                || info.getType() == ConnectivityManager.TYPE_WIFI));
    }

    /**
     * Compares two json formatted updates list files
     *
     * @param oldJson old update list
     * @param newJson new update list
     * @return true if newJson has at least a compatible update not available in oldJson
     * @throws IOException   may throw IOException
     * @throws JSONException may throw JSONException
     */
    public static boolean checkForNewUpdates(File oldJson, File newJson, Context context)
            throws IOException, JSONException {
        List<UpdateInfo> oldList = parseJson(oldJson, true, context);
        List<UpdateInfo> newList = parseJson(newJson, true, context);
        Set<String> oldIds = new HashSet<>();
        for (UpdateInfo update : oldList) {
            oldIds.add(update.getDownloadId());
        }
        // In case of no new updates, the old list should
        // have all (if not more) the updates
        for (UpdateInfo update : newList) {
            if (!oldIds.contains(update.getDownloadId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the offset to the compressed data of a file inside the given zip
     *
     * @param zipFile   input zip file
     * @param entryPath full path of the entry
     * @return the offset of the compressed, or -1 if not found
     * @throws IllegalArgumentException if the given entry is not found
     */
    public static long getZipEntryOffset(ZipFile zipFile, String entryPath) {
        // Each entry has an header of (30 + n + m) bytes
        // 'n' is the length of the file name
        // 'm' is the length of the extra field
        final int FIXED_HEADER_SIZE = 30;
        Enumeration<? extends ZipEntry> zipEntries = zipFile.entries();
        long offset = 0;
        while (zipEntries.hasMoreElements()) {
            ZipEntry entry = zipEntries.nextElement();
            int n = entry.getName().length();
            int m = entry.getExtra() == null ? 0 : entry.getExtra().length;
            int headerSize = FIXED_HEADER_SIZE + n + m;
            offset += headerSize;
            if (entry.getName().equals(entryPath)) {
                return offset;
            }
            offset += entry.getCompressedSize();
        }
        Log.e(TAG, "Entry " + entryPath + " not found");
        throw new IllegalArgumentException("The given entry was not found");
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private static void removeUncryptFiles(File downloadPath) {
        File[] uncryptFiles = downloadPath.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(Constants.UNCRYPT_FILE_EXT);
            }
        });
        if (uncryptFiles == null) {
            return;
        }
        for (File file : uncryptFiles) {
            file.delete();
        }
    }

    /**
     * Cleanup the download directory, which is assumed to be a privileged location
     * the user can't access and that might have stale files. This can happen if
     * the data of the application are wiped.
     *
     * @param context Context for getDownloadPath
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static void cleanupDownloadsDir(Context context) {
        File downloadPath = getDownloadPath(context);
        if (downloadPath == null) {
            Log.e(TAG, "Failed to access download path!");
            return;
        }
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);

        removeUncryptFiles(downloadPath);

        long buildTimestamp = SystemProperties.getLong(Constants.PROP_BUILD_DATE, 0);
        long prevTimestamp = preferences.getLong(Constants.PREF_INSTALL_OLD_TIMESTAMP, 0);
        String lastUpdatePath = preferences.getString(Constants.PREF_INSTALL_PACKAGE_PATH, null);
        boolean reinstalling = preferences.getBoolean(Constants.PREF_INSTALL_AGAIN, false);
        boolean deleteUpdates = preferences.getBoolean(Constants.PREF_AUTO_DELETE_UPDATES, false);
        if ((buildTimestamp != prevTimestamp || reinstalling) && deleteUpdates &&
                lastUpdatePath != null) {
            File lastUpdate = new File(lastUpdatePath);
            if (lastUpdate.exists()) {
                lastUpdate.delete();
                // Remove the pref not to delete the file if re-downloaded
                preferences.edit().remove(Constants.PREF_INSTALL_PACKAGE_PATH).apply();
            }
        }

        final String DOWNLOADS_CLEANUP_DONE = "cleanup_done";
        if (preferences.getBoolean(DOWNLOADS_CLEANUP_DONE, false)) {
            return;
        }

        Log.d(TAG, "Cleaning " + downloadPath);
        if (!downloadPath.isDirectory()) {
            return;
        }
        File[] files = downloadPath.listFiles();
        if (files == null) {
            return;
        }

        // Ideally the database is empty when we get here
        UpdatesDbHelper dbHelper = new UpdatesDbHelper(context);
        List<String> knownPaths = new ArrayList<>();
        for (UpdateInfo update : dbHelper.getUpdates()) {
            knownPaths.add(update.getFile().getAbsolutePath());
        }
        for (File file : files) {
            if (!knownPaths.contains(file.getAbsolutePath())) {
                Log.d(TAG, "Deleting " + file.getAbsolutePath());
                file.delete();
            }
        }

        preferences.edit().putBoolean(DOWNLOADS_CLEANUP_DONE, true).apply();
    }

    public static File appendSequentialNumber(final File file) {
        String name;
        String extension;
        int extensionPosition = file.getName().lastIndexOf(".");
        if (extensionPosition > 0) {
            name = file.getName().substring(0, extensionPosition);
            extension = file.getName().substring(extensionPosition);
        } else {
            name = file.getName();
            extension = "";
        }
        final File parent = file.getParentFile();
        for (int i = 1; i < Integer.MAX_VALUE; i++) {
            File newFile = new File(parent, name + "-" + i + extension);
            if (!newFile.exists()) {
                return newFile;
            }
        }
        throw new IllegalStateException();
    }

    public static boolean isABDevice() {
        return SystemProperties.getBoolean(Constants.PROP_AB_DEVICE, false);
    }

    private static boolean isABUpdate(ZipFile zipFile) {
        return zipFile.getEntry(Constants.AB_PAYLOAD_BIN_PATH) != null &&
                zipFile.getEntry(Constants.AB_PAYLOAD_PROPERTIES_PATH) != null;
    }

    public static boolean isABUpdate(File file) throws IOException {
        ZipFile zipFile = new ZipFile(file);
        boolean isAB = isABUpdate(zipFile);
        zipFile.close();
        return isAB;
    }

    public static boolean isEncrypted(Context context, File file) {
        StorageManager sm = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
        return sm.isEncrypted(file);
    }

    public static int getUpdateCheckSetting(Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        return preferences.getInt(Constants.PREF_AUTO_UPDATES_CHECK_INTERVAL,
                Constants.AUTO_UPDATES_CHECK_INTERVAL_WEEKLY);
    }

    public static void setUpdateCheckSetting(Context context, int interval) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        preferences.edit().putInt(Constants.PREF_AUTO_UPDATES_CHECK_INTERVAL, interval).apply();
    }

    public static boolean isUpdateCheckEnabled(Context context) {
        return getUpdateCheckSetting(context) != Constants.AUTO_UPDATES_CHECK_INTERVAL_NEVER;
    }

    public static long getUpdateCheckInterval(Context context) {
        switch (Utils.getUpdateCheckSetting(context)) {
            case Constants.AUTO_UPDATES_CHECK_INTERVAL_DAILY:
                return AlarmManager.INTERVAL_DAY;
            case Constants.AUTO_UPDATES_CHECK_INTERVAL_WEEKLY:
            default:
                return AlarmManager.INTERVAL_DAY * 7;
            case Constants.AUTO_UPDATES_CHECK_INTERVAL_MONTHLY:
                return AlarmManager.INTERVAL_DAY * 30;
        }
    }

    public static String getDevice() {
        return SystemProperties.get(getProjectProp(Constants.PROP_DEVICE));
    }

    public static String getModel() {
        return SystemProperties.get(Constants.PROP_MODEL);
    }

    public static String getReleaseType(Context context) {
        String type = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(Constants.PREF_RELEASE_TYPE, Constants.DEFAULT_RELEASE_TYPE);
        if (type == null || type.equals(Constants.DEFAULT_RELEASE_TYPE))
            return SystemProperties.get(getProjectProp(Constants.PROP_RELEASE_TYPE)).toLowerCase(Locale.ROOT);
        else return type.toLowerCase(Locale.ROOT);
    }

    public static void setReleaseType(Context context, String type) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        preferences.edit().putString(Constants.PREF_RELEASE_TYPE, type).apply();
    }

    public static String getBuildDate(Context context) {
        return StringGenerator.getDateTimeLocalized(context, Long.parseLong(SystemProperties.get(Constants.PROP_BUILD_DATE)));
    }

    public static String getBuildVersion() {
        return SystemProperties.get(getProjectProp(Constants.PROP_BUILD_VERSION));
    }

    public static String getProp(String prop) {
        return SystemProperties.get(prop);
    }

    public static void setPerformanceMode(Context context, UpdaterController updaterController, boolean enable) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        preferences.edit().putBoolean(Constants.PREF_AB_PERF_MODE, enable).apply();
        updaterController.setPerformanceMode(enable);
    }

    public static boolean getPerformanceMode(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(Constants.PREF_AB_PERF_MODE, false);
    }

    static String getProjectProp(String prop) {
        return prop.replace("{project}", SystemProperties.get(Constants.PROP_PROJECT_NAME));
    }

    public static boolean getVerify(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(Constants.PREF_VERIFY_MODE, true);
    }

    public static void setVerify(Context context, boolean enable) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        preferences.edit().putBoolean(Constants.PREF_VERIFY_MODE, enable).apply();
    }
}
