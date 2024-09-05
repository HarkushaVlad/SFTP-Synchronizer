package com.vhark.sftp_synchronizer.handler;

import static com.vhark.sftp_synchronizer.UI.UIComponents.isVisible;
import static com.vhark.sftp_synchronizer.constant.PrefsKeys.KEY_AUTO_CONFIRM_ENABLED;
import static com.vhark.sftp_synchronizer.constant.PrefsKeys.KEY_DEFAULT_SFTP_PATH;
import static com.vhark.sftp_synchronizer.constant.PrefsKeys.KEY_PASSWORD;
import static com.vhark.sftp_synchronizer.constant.PrefsKeys.KEY_PHONE_PATH;
import static com.vhark.sftp_synchronizer.constant.PrefsKeys.KEY_SFTP_ADDRESS;
import static com.vhark.sftp_synchronizer.constant.PrefsKeys.KEY_SFTP_PATH;
import static com.vhark.sftp_synchronizer.constant.PrefsKeys.KEY_USERNAME;

import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.transition.TransitionManager;
import android.view.View;
import android.widget.Button;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.lifecycle.ViewModelProvider;

import com.vhark.sftp_synchronizer.MainActivity;
import com.vhark.sftp_synchronizer.R;
import com.vhark.sftp_synchronizer.UI.UIComponents;
import com.vhark.sftp_synchronizer.constant.LogsPath;
import com.vhark.sftp_synchronizer.constant.PrefsConstants;
import com.vhark.sftp_synchronizer.exception.LogFileCreationException;
import com.vhark.sftp_synchronizer.fragment.AutoConfirmationDialogFragment;
import com.vhark.sftp_synchronizer.fragment.SftpSettingsDialogFragment;
import com.vhark.sftp_synchronizer.shared.SharedViewModel;
import com.vhark.sftp_synchronizer.storage.StorageUtils;

import java.io.File;

public class EventHandler {

    private final MainActivity activity;
    private final UIComponents uiComponents;
    private final PermissionHandler permissionHandler;
    private final SharedViewModel viewModel;
    private final ActivityResultLauncher<Intent> selectFileLauncher;
    private final ActivityResultLauncher<Intent> selectDirectoryLauncher;

    private boolean isHowToExpanded = false;
    private boolean backPressedOnce = false;

    public EventHandler(
            MainActivity activity,
            UIComponents uiComponents,
            PermissionHandler permissionHandler,
            ActivityResultLauncher<Intent> selectFileLauncher,
            ActivityResultLauncher<Intent> selectDirectoryLauncher) {
        this.activity = activity;
        this.uiComponents = uiComponents;
        this.permissionHandler = permissionHandler;
        this.selectFileLauncher = selectFileLauncher;
        this.selectDirectoryLauncher = selectDirectoryLauncher;
        viewModel = new ViewModelProvider(activity).get(SharedViewModel.class);
        setupListeners();
        loadAllPrefs();
        updateAutoPasteButton();
        updateSyncButton();
    }

    private void setupListeners() {
        uiComponents.getOpenMenuButton().setOnClickListener(v -> openMenu());
        uiComponents
                .getAutoConfirmCheckBox()
                .setOnClickListener(v -> autoConfirmationCheckBoxClick());
        uiComponents.getChooseDirectoryButton().setOnClickListener(v -> openDirectoryPicker());
        uiComponents.getAutoPasteButton().setOnClickListener(v -> proceedAutoPaste());
        uiComponents.getSyncButton().setOnClickListener(v -> syncButtonClick());
        uiComponents.getChooseButton().setOnClickListener(v -> chooseButtonClick());
        uiComponents.getChooseDirectoryButton().setOnClickListener(v -> openDirectoryPicker());
        uiComponents.getChooseFileButton().setOnClickListener(v -> openFilePicker());
        uiComponents.getOpenLogsButton().setOnClickListener(v -> openLogFile(LogsPath.LOGS));
        uiComponents.getCloseOverlayButton().setOnClickListener(v -> uiComponents.hideOverlay());
        uiComponents.getToggleHowToButton().setOnClickListener(v -> toggleHowToScrollView());
        uiComponents.getFabScrollToTop().setOnClickListener(v -> scrollToTop());
        uiComponents.getPathPhoneInput().addTextChangedListener(new PathTextWatcher());
        uiComponents.getPathSftpInput().addTextChangedListener(new PathTextWatcher());
        setupSharedObservers();
        setupBackButtonHandler();
    }

    private void openDirectoryPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        selectDirectoryLauncher.launch(intent);
    }

    public void handleFilePicked(String path) {
        uiComponents.getPathPhoneInput().setText(path);
        PrefsConstants.instance().storeValueString(KEY_PHONE_PATH, path);
        uiComponents.updateTextViews();
        resetChooseButtonsState();
        autoPasteSftpPath();
    }

    public void handleDirectoryPicked(String path) {
        uiComponents.getPathPhoneInput().setText(path);
        PrefsConstants.instance().storeValueString(KEY_PHONE_PATH, path);
        uiComponents.updateTextViews();
        resetChooseButtonsState();
        autoPasteSftpPath();
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false);
        selectFileLauncher.launch(intent);
    }

    private void setupSharedObservers() {
        viewModel
                .getCancelRequested()
                .observe(activity, requested -> activity.setCancelRequested(true));
        viewModel
                .getUpdateButtonRequested()
                .observe(
                        activity,
                        requested -> {
                            updateAutoPasteButton();
                            updateSyncButton();
                        });
    }

    private void setupBackButtonHandler() {
        activity.getOnBackPressedDispatcher()
                .addCallback(
                        activity,
                        new OnBackPressedCallback(true) {
                            @Override
                            public void handleOnBackPressed() {
                                handleBackButtonPress();
                            }
                        });
    }

    public void resetChooseButtonsState() {
        if (isVisible(uiComponents.getChooseDirectoryButton())
                || isVisible(uiComponents.getChooseFileButton())) {
            uiComponents.animateFileButtonBack();
        }
    }

    private void openMenu() {
        SftpSettingsDialogFragment dialog = new SftpSettingsDialogFragment();
        dialog.show(activity.getSupportFragmentManager(), "SftpSettingsDialog");
    }

    private void autoPasteSftpPath() {
        String storedDefaultSftpPath =
                PrefsConstants.instance().fetchValueString(KEY_DEFAULT_SFTP_PATH);

        if (storedDefaultSftpPath.isBlank()) {
            return;
        }

        String sparePath =
                PrefsConstants.instance()
                        .fetchValueString(KEY_PHONE_PATH)
                        .replaceAll(
                                Environment.getExternalStorageDirectory().getAbsolutePath(), "");
        String absoluteSftpPath = storedDefaultSftpPath + sparePath;
        uiComponents.getPathSftpInput().setText(absoluteSftpPath);
        uiComponents.showToast("SFTP path has been auto-pasted");
        PrefsConstants.instance().storeValueString(KEY_SFTP_PATH, absoluteSftpPath);
        uiComponents.updateTextViews();
    }

    private void autoConfirmationCheckBoxClick() {
        if (uiComponents.getAutoConfirmCheckBox().isChecked()) {
            uiComponents.getAutoConfirmCheckBox().setChecked(false);
            AutoConfirmationDialogFragment dialog = new AutoConfirmationDialogFragment();
            dialog.show(activity.getSupportFragmentManager(), "AutoConfirmationDialog");
        } else {
            PrefsConstants.instance().storeValueBoolean(KEY_AUTO_CONFIRM_ENABLED, false);
        }
    }

    private void proceedAutoPaste() {
        updateAutoPasteButton();
        if (PrefsConstants.instance().fetchValueString(KEY_DEFAULT_SFTP_PATH).isBlank()) {
            uiComponents.showToast(activity.getString(R.string.set_base_sftp_directory));
        } else if (uiComponents.getPathPhoneInput().getText().toString().isBlank()) {
            uiComponents.showToast(activity.getString(R.string.phone_path_empty));
        } else {
            autoPasteSftpPath();
        }
    }

    private void syncButtonClick() {
        updateSyncButton();
        PrefsConstants.instance()
                .storeValueString(
                        KEY_PHONE_PATH, uiComponents.getPathPhoneInput().getText().toString());
        PrefsConstants.instance()
                .storeValueString(
                        KEY_SFTP_PATH, uiComponents.getPathSftpInput().getText().toString());
        uiComponents.updateTextViews();
        activity.startSynchronization();
    }

    private void chooseButtonClick() {
        uiComponents.getChooseButton().setVisibility(View.GONE);
        uiComponents.getChooseDirectoryButton().setVisibility(View.VISIBLE);
        uiComponents.animateFileButton();
    }

    private void handleBackButtonPress() {
        if (isVisible(uiComponents.getCloseOverlayButton())) {
            uiComponents.hideOverlay();
            return;
        }

        if (isHowToExpanded && isVisible(uiComponents.getFabScrollToTop())) {
            scrollToTop();
            return;
        }

        if (!isVisible(uiComponents.getOverlayLayout())) {
            if (backPressedOnce) {
                activity.finishAffinity();
            } else {
                uiComponents.showToast("Press back again to exit");
                backPressedOnce = true;
                new Handler().postDelayed(() -> backPressedOnce = false, 2000);
            }
        }
    }

    private void toggleHowToScrollView() {
        ConstraintLayout howToConstraintLayout = uiComponents.getHowToConstraintLayout();
        TransitionManager.beginDelayedTransition(howToConstraintLayout);
        ConstraintSet constraintSet = new ConstraintSet();
        constraintSet.clone(howToConstraintLayout);

        if (isHowToExpanded) {
            wrapOpenHowToSection(constraintSet);
        } else {
            expandHowToSection(constraintSet);
        }

        constraintSet.applyTo(howToConstraintLayout);
        isHowToExpanded = !isHowToExpanded;
    }

    private void expandHowToSection(ConstraintSet constraintSet) {
        constraintSet.constrainHeight(R.id.howToNestedFrameLayout, ConstraintSet.WRAP_CONTENT);
        uiComponents.setHowToViewVisible();
    }

    private void wrapOpenHowToSection(ConstraintSet constraintSet) {
        constraintSet.constrainHeight(R.id.howToNestedFrameLayout, 0);
        uiComponents.hideHowToView();
    }

    private void scrollToTop() {
        uiComponents.getMainScrollView().smoothScrollTo(0, 0);
    }

    public void openLogFile(LogsPath logsPath) {
        File logFile;
        try {
            logFile = StorageUtils.getLogFile(activity, logsPath);
        } catch (LogFileCreationException e) {
            uiComponents.showToast(activity.getString(R.string.cannot_open_log_file));
            return;
        }

        if (logFile.exists()) {
            Uri fileUri =
                    FileProvider.getUriForFile(
                            activity, "com.vhark.sftp_synchronizer.fileprovider", logFile);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setDataAndType(fileUri, "text/plain");

            if (activity.getPackageManager().queryIntentActivities(intent, 0).isEmpty()) {
                intent.setDataAndType(fileUri, "*/*");
            }

            Intent chooser =
                    Intent.createChooser(intent, activity.getString(R.string.open_log_file_with));
            activity.startActivity(chooser);
        } else {
            uiComponents.showToast(activity.getString(R.string.log_file_not_exist));
        }
    }

    private void updateAutoPasteButton() {
        Button autoPasteButton = uiComponents.getAutoPasteButton();
        if (PrefsConstants.instance().fetchValueString(KEY_DEFAULT_SFTP_PATH).isBlank()) {
            autoPasteButton.setBackgroundResource(R.drawable.inactive_button);
            autoPasteButton.setTextColor(ContextCompat.getColor(activity, R.color.hintText));
        } else {
            autoPasteButton.setBackgroundResource(R.drawable.button);
            autoPasteButton.setTextColor(ContextCompat.getColor(activity, R.color.textColor));
        }
    }

    public void updateSyncButton() {
        if (permissionHandler.isOldPermissionVersion()
                && permissionHandler.isOldPermissionsNotGranted()) {
            uiComponents.setSyncButtonInactive();
            return;
        }

        if (isSftpCredentialsNotFilled()) {
            uiComponents.setSyncButtonInactive();
            return;
        }

        String[] phonePaths = uiComponents.getPathPhoneInput().getText().toString().split("/");
        String[] sftpPaths = uiComponents.getPathSftpInput().getText().toString().split("/");

        if (String.join("", phonePaths).isEmpty() || String.join("", sftpPaths).isEmpty()) {
            uiComponents.setSyncButtonInactive();
            return;
        }

        String phonePath = phonePaths[phonePaths.length - 1];
        String sftpPath = sftpPaths[sftpPaths.length - 1];

        if (phonePath.equals(sftpPath)) {
            uiComponents.setSyncButtonActive();
            return;
        }

        uiComponents.setSyncButtonInactive();
    }

    private boolean isSftpCredentialsNotFilled() {
        String sftpAddress = PrefsConstants.instance().fetchValueString(KEY_SFTP_ADDRESS);
        String user = PrefsConstants.instance().fetchValueString(KEY_USERNAME);
        String password = PrefsConstants.instance().fetchValueString(KEY_PASSWORD);
        return sftpAddress.isBlank() || user.isBlank() || password.isBlank();
    }

    public void handleSyncButtonDisabled() {
        if (permissionHandler.isOldPermissionVersion()
                && permissionHandler.isOldPermissionsNotGranted()) {
            uiComponents.showToast(
                    activity.getString(R.string.permission_for_storage_should_granted));
            return;
        }
        if (isSftpCredentialsNotFilled()) {
            uiComponents.showToast(activity.getString(R.string.sftp_credentials_should_filled));
            return;
        }

        if (uiComponents.getPathPhoneInput().getText().length() == 0
                || uiComponents.getPathSftpInput().getText().length() == 0) {
            uiComponents.showToast(activity.getString(R.string.paths_cant_be_empty));
            return;
        }

        uiComponents.showToast(activity.getString(R.string.paths_must_have_same_target));
    }

    private void loadAllPrefs() {
        uiComponents
                .getPathPhoneInput()
                .setText(PrefsConstants.instance().fetchValueString(KEY_PHONE_PATH));
        uiComponents
                .getPathSftpInput()
                .setText(PrefsConstants.instance().fetchValueString(KEY_SFTP_PATH));
        uiComponents.updateTextViews();
        uiComponents
                .getAutoConfirmCheckBox()
                .setChecked(PrefsConstants.instance().fetchValueBoolean(KEY_AUTO_CONFIRM_ENABLED));
    }

    private class PathTextWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            uiComponents.updateTextViews();
            updateSyncButton();
        }

        @Override
        public void afterTextChanged(Editable s) {}
    }
}
