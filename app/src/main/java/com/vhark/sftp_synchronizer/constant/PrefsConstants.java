package com.vhark.sftp_synchronizer.constant;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

public class PrefsConstants {

    static PrefsConstants _instance;

    private static final String PREFS_NAME = "SftpSettingsPrefs";

    Context context;
    SharedPreferences sharedPref;
    SharedPreferences.Editor sharedPrefEditor;

    public static PrefsConstants instance(Context context) {
        if (_instance == null) {
            _instance = new PrefsConstants();
            _instance.configSessionUtils(context);
        }
        return _instance;
    }

    public static PrefsConstants instance() {
        return _instance;
    }

    public void configSessionUtils(Context context) {
        this.context = context;
        sharedPref = context.getSharedPreferences(PREFS_NAME, Activity.MODE_PRIVATE);
        sharedPrefEditor = sharedPref.edit();
    }

    public void storeValueString(PrefsKeys key, String value) {
        sharedPrefEditor.putString(key.toString(), value);
        sharedPrefEditor.commit();
    }

    public void storeValueBoolean(PrefsKeys key, boolean value) {
        sharedPrefEditor.putBoolean(key.toString(), value);
        sharedPrefEditor.commit();
    }

    public String fetchValueString(PrefsKeys key) {
        return sharedPref.getString(key.toString(), "");
    }

    public boolean fetchValueBoolean(PrefsKeys key) {
        return sharedPref.getBoolean(key.toString(), false);
    }
}
