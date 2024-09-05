package com.vhark.sftp_synchronizer;


import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.vhark.sftp_synchronizer.UI.UIComponents;
import com.vhark.sftp_synchronizer.constant.LogsPath;
import com.vhark.sftp_synchronizer.constant.PrefsConstants;
import com.vhark.sftp_synchronizer.handler.EventHandler;
import com.vhark.sftp_synchronizer.handler.PermissionHandler;
import com.vhark.sftp_synchronizer.handler.SynchronizationHandler;
import com.vhark.sftp_synchronizer.shared.OperationExecutionListener;

import lombok.Getter;
import lombok.Setter;

public class MainActivity extends AppCompatActivity implements OperationExecutionListener {

    private UIComponents uiComponents;
    private EventHandler eventHandler;
    private PermissionHandler permissionHandler;
    private SynchronizationHandler synchronizationHandler;

    @Getter @Setter private volatile boolean cancelRequested = false;

    private final ActivityResultLauncher<Intent> selectFileLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK
                                && result.getData() != null
                                && result.getData().getData() != null) {
                            String path = replacePrimaryAndroidPath(result, "/document/primary:");
                            eventHandler.handleFilePicked(path);
                        }
                    });

    private final ActivityResultLauncher<Intent> selectDirectoryLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK
                                && result.getData() != null
                                && result.getData().getData() != null) {
                            String path = replacePrimaryAndroidPath(result, "/tree/.*:");
                            eventHandler.handleDirectoryPicked(path);
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        PrefsConstants.instance(this.getApplicationContext());
        uiComponents = new UIComponents(this);
        permissionHandler = new PermissionHandler(this);
        eventHandler =
                new EventHandler(
                        this,
                        uiComponents,
                        permissionHandler,
                        selectFileLauncher,
                        selectDirectoryLauncher);
        synchronizationHandler = new SynchronizationHandler(this, uiComponents, eventHandler);
        setVersionText();
    }

    public void startSynchronization() {
        synchronizationHandler.synchronize();
    }

    public void openLogFile(LogsPath logsPath) {
        eventHandler.openLogFile(logsPath);
    }

    private void setVersionText() {
        String versionName;
        try {
            versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return;
        }
        uiComponents.getVersionTextView().setText(versionName);
    }

    public void resetChooseButtonsState(View view) {
        eventHandler.resetChooseButtonsState();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (permissionHandler.isOldPermissionVersion()
                && requestCode == PermissionHandler.REQUEST_CODE_OLD_PERMISSIONS) {
            eventHandler.updateSyncButton();
        }
    }

    @Override
    public void onOperationExecute(String information) {
        runOnUiThread(() -> uiComponents.getOverlayTextView().append(information));
    }

    private String replacePrimaryAndroidPath(ActivityResult result, String regex) {
        return result.getData()
                .getData()
                .getPath()
                .replaceAll(
                        regex, Environment.getExternalStorageDirectory().getAbsolutePath() + "/");
    }
}
