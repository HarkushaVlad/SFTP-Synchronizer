package com.vhark.sftp_synchronizer.handler;

import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;

import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.vhark.sftp_synchronizer.MainActivity;
import com.vhark.sftp_synchronizer.fragment.PermissionDialogFragment;

public class PermissionHandler {

    private final MainActivity activity;

    public static final int REQUEST_CODE_OLD_PERMISSIONS = 1;
    public static final String[] REQUIRED_OLD_PERMISSIONS = {
        READ_EXTERNAL_STORAGE, WRITE_EXTERNAL_STORAGE
    };

    public PermissionHandler(MainActivity activity) {
        this.activity = activity;
        requestPermissions();
    }

    private void requestPermissions() {
        if (!isOldPermissionVersion() && !Environment.isExternalStorageManager()) {
            PermissionDialogFragment dialog = new PermissionDialogFragment();
            dialog.show(activity.getSupportFragmentManager(), "PermissionDialog");
        } else if (isOldPermissionsNotGranted()) {
            ActivityCompat.requestPermissions(
                    activity, REQUIRED_OLD_PERMISSIONS, REQUEST_CODE_OLD_PERMISSIONS);
        }
    }

    public boolean isOldPermissionsNotGranted() {
        for (String permission : REQUIRED_OLD_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(activity, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return true;
            }
        }
        return false;
    }

    public boolean isOldPermissionVersion() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.R;
    }
}
