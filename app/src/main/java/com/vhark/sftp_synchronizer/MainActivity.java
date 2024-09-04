package com.vhark.sftp_synchronizer;

import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;

import static com.vhark.sftp_synchronizer.UI.UIComponents.isVisible;
import static com.vhark.sftp_synchronizer.constant.LogTags.PHONE_LOG_TAG;
import static com.vhark.sftp_synchronizer.constant.LogTags.SFTP_LOG_TAG;
import static com.vhark.sftp_synchronizer.constant.PrefsKeys.*;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.transition.TransitionManager;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.lifecycle.ViewModelProvider;

import com.jcraft.jsch.SftpException;
import com.vhark.sftp_synchronizer.UI.UIComponents;
import com.vhark.sftp_synchronizer.constant.LogsPath;
import com.vhark.sftp_synchronizer.constant.PrefsConstants;
import com.vhark.sftp_synchronizer.constant.PrefsKeys;
import com.vhark.sftp_synchronizer.exception.BothPathsNotExistException;
import com.vhark.sftp_synchronizer.exception.DifferentDirectoriesNamesException;
import com.vhark.sftp_synchronizer.exception.IncompatiblePathException;
import com.vhark.sftp_synchronizer.exception.InvalidSftpCredentialsException;
import com.vhark.sftp_synchronizer.exception.InvalidTargetFilePathException;
import com.vhark.sftp_synchronizer.exception.LogFileCreationException;
import com.vhark.sftp_synchronizer.exception.PathsLoggingException;
import com.vhark.sftp_synchronizer.fragment.AutoConfirmationDialogFragment;
import com.vhark.sftp_synchronizer.fragment.ConfirmationDialogFragment;
import com.vhark.sftp_synchronizer.fragment.PermissionDialogFragment;
import com.vhark.sftp_synchronizer.fragment.SftpSettingsDialogFragment;
import com.vhark.sftp_synchronizer.shared.OperationExecutionListener;
import com.vhark.sftp_synchronizer.shared.SharedViewModel;
import com.vhark.sftp_synchronizer.storage.PhoneStorage;
import com.vhark.sftp_synchronizer.storage.SftpStorage;
import com.vhark.sftp_synchronizer.storage.StorageUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

public class MainActivity extends AppCompatActivity implements OperationExecutionListener {

    private UIComponents uiComponents;
    private SharedViewModel viewModel;

    private volatile boolean cancelRequested = false;
    private boolean isHowToExpanded = false;
    private boolean backPressedOnce = false;

    private static final int REQUEST_CODE_OLD_PERMISSIONS = 1;
    private static final String[] REQUIRED_OLD_PERMISSIONS = {
        READ_EXTERNAL_STORAGE, WRITE_EXTERNAL_STORAGE
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        PrefsConstants.instance(this.getApplicationContext());
        uiComponents = new UIComponents(this);
        viewModel = new ViewModelProvider(this).get(SharedViewModel.class);
        requestPermissions();
        loadAllPrefs();
        setupListeners();
        updateAutoPasteButton();
        updateSyncButton();
        setVersionText();
    }

    private void openDirectoryPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        selectDirectoryLauncher.launch(intent);
    }

    private final ActivityResultLauncher<Intent> selectDirectoryLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK
                                && result.getData() != null
                                && result.getData().getData() != null) {
                            String path = replacePrimaryAndroidPath(result, "/tree/.*:");
                            uiComponents.getPathPhoneInput().setText(path);
                            storeValue(KEY_PHONE_PATH, path);
                            uiComponents.updateTextViews();
                            resetChooseButtonsState();
                            autoPasteSftpPath();
                        }
                    });

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false);
        selectFileLauncher.launch(intent);
    }

    private final ActivityResultLauncher<Intent> selectFileLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK
                                && result.getData() != null
                                && result.getData().getData() != null) {
                            String path = replacePrimaryAndroidPath(result, "/document/primary:");
                            uiComponents.getPathPhoneInput().setText(path);
                            storeValue(KEY_PHONE_PATH, path);
                            uiComponents.updateTextViews();
                            resetChooseButtonsState();
                            autoPasteSftpPath();
                        }
                    });

    private String replacePrimaryAndroidPath(ActivityResult result, String regex) {
        return result.getData()
                .getData()
                .getPath()
                .replaceAll(
                        regex, Environment.getExternalStorageDirectory().getAbsolutePath() + "/");
    }

    private void storeValue(PrefsKeys key, String value) {
        PrefsConstants.instance().storeValueString(key, value.trim());
    }

    private void storeValue(PrefsKeys key, boolean value) {
        PrefsConstants.instance().storeValueBoolean(key, value);
    }

    private String loadStoredStringValue(PrefsKeys key) {
        return PrefsConstants.instance().fetchValueString(key);
    }

    private boolean loadStoredBooleanValue(PrefsKeys key) {
        return PrefsConstants.instance().fetchValueBoolean(key);
    }

    private void loadStoredValueIntoInput(EditText input, PrefsKeys key) {
        input.setText(PrefsConstants.instance().fetchValueString(key));
    }

    private void storeAllDirectoryPaths() {
        storeValue(KEY_PHONE_PATH, uiComponents.getPathPhoneInput().getText().toString());
        storeValue(KEY_SFTP_PATH, uiComponents.getPathSftpInput().getText().toString());
        uiComponents.updateTextViews();
    }

    private void loadAllPrefs() {
        loadStoredValueIntoInput(uiComponents.getPathPhoneInput(), KEY_PHONE_PATH);
        loadStoredValueIntoInput(uiComponents.getPathSftpInput(), KEY_SFTP_PATH);
        uiComponents.updateTextViews();
        uiComponents
                .getAutoConfirmCheckBox()
                .setChecked(loadStoredBooleanValue(KEY_AUTO_CONFIRM_ENABLED));
    }

    private void autoPasteSftpPath() {
        String storedDefaultSftpPath = loadStoredStringValue(KEY_DEFAULT_SFTP_PATH);

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
        storeValue(KEY_SFTP_PATH, absoluteSftpPath);
        uiComponents.updateTextViews();
    }

    public void updateAutoPasteButton() {
        Button autoPasteButton = findViewById(R.id.autoPasteButton);
        if (loadStoredStringValue(KEY_DEFAULT_SFTP_PATH).isBlank()) {
            autoPasteButton.setBackgroundResource(R.drawable.inactive_button);
            autoPasteButton.setTextColor(ContextCompat.getColor(this, R.color.hintText));
        } else {
            autoPasteButton.setBackgroundResource(R.drawable.button);
            autoPasteButton.setTextColor(ContextCompat.getColor(this, R.color.textColor));
        }
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

    private void updateSyncButton() {
        if (isOldPermissionVersion() && !oldPermissionsGranted()) {
            uiComponents.setSyncButtonInactive();
            return;
        }

        if (!isSftpCredentialsFilled()) {
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

    private boolean isSftpCredentialsFilled() {
        String sftpAddress = PrefsConstants.instance().fetchValueString(KEY_SFTP_ADDRESS);
        String user = PrefsConstants.instance().fetchValueString(KEY_USERNAME);
        String password = PrefsConstants.instance().fetchValueString(KEY_PASSWORD);
        return !sftpAddress.isBlank() && !user.isBlank() && !password.isBlank();
    }

    private void requestPermissions() {
        if (!isOldPermissionVersion() && !Environment.isExternalStorageManager()) {
            PermissionDialogFragment dialog = new PermissionDialogFragment();
            dialog.show(getSupportFragmentManager(), "PermissionDialog");
        } else if (!oldPermissionsGranted()) {
            ActivityCompat.requestPermissions(
                    this, REQUIRED_OLD_PERMISSIONS, REQUEST_CODE_OLD_PERMISSIONS);
        }
    }

    private boolean oldPermissionsGranted() {
        for (String permission : REQUIRED_OLD_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private boolean isOldPermissionVersion() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.R;
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (isOldPermissionVersion() && requestCode == REQUEST_CODE_OLD_PERMISSIONS) {
            updateSyncButton();
        }
    }

    @Override
    public void onOperationExecute(String information) {
        runOnUiThread(() -> uiComponents.getOverlayTextView().append(information));
    }

    private void showConfirmationDialog(String title, String message, CountDownLatch latch) {
        runOnUiThread(
                () -> {
                    ConfirmationDialogFragment dialog =
                            ConfirmationDialogFragment.newInstance(title, message, latch);
                    dialog.setCancelable(false);
                    dialog.show(getSupportFragmentManager(), "ConfirmationDialog");
                });
    }

    public int getValueInPxFromDp(int valueInDp) {
        return (int) (valueInDp * getResources().getDisplayMetrics().density);
    }

    private void setVersionText() {
        String versionName = null;
        try {
            versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return;
        }
        uiComponents.getVersionTextView().setText(versionName);
    }

    private void openMenu() {
        SftpSettingsDialogFragment dialog = new SftpSettingsDialogFragment();
        dialog.show(getSupportFragmentManager(), "SftpSettingsDialog");
    }

    private void autoConfirmationCheckBoxClick() {
        if (uiComponents.getAutoConfirmCheckBox().isChecked()) {
            uiComponents.getAutoConfirmCheckBox().setChecked(false);
            AutoConfirmationDialogFragment dialog = new AutoConfirmationDialogFragment();
            dialog.show(getSupportFragmentManager(), "AutoConfirmationDialog");
        } else {
            storeValue(KEY_AUTO_CONFIRM_ENABLED, false);
        }
    }

    private void proceedAutoPaste() {
        updateAutoPasteButton();
        if (loadStoredStringValue(KEY_DEFAULT_SFTP_PATH).isBlank()) {
            uiComponents.showToast(getString(R.string.set_base_sftp_directory));
        } else if (uiComponents.getPathPhoneInput().getText().toString().isBlank()) {
            uiComponents.showToast(getString(R.string.phone_path_empty));
        } else {
            autoPasteSftpPath();
        }
    }

    private void syncButtonClick() {
        updateSyncButton();
        storeAllDirectoryPaths();
        synchronize();
    }

    private void chooseButtonClick() {
        uiComponents.getChooseButton().setVisibility(View.GONE);
        uiComponents.getChooseDirectoryButton().setVisibility(View.VISIBLE);
        uiComponents.animateFileButton();
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

    private void setupSharedObservers() {
        viewModel.getCancelRequested().observe(this, requested -> cancelRequested = true);
        viewModel
                .getUpdateButtonRequested()
                .observe(
                        this,
                        requested -> {
                            updateAutoPasteButton();
                            updateSyncButton();
                        });
    }

    private void setupBackButtonHandler() {
        getOnBackPressedDispatcher()
                .addCallback(
                        this,
                        new OnBackPressedCallback(true) {
                            @Override
                            public void handleOnBackPressed() {
                                handleBackButtonPress();
                            }
                        });
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
                finishAffinity();
            } else {
                uiComponents.showToast("Press back again to exit");
                backPressedOnce = true;
                new Handler().postDelayed(() -> backPressedOnce = false, 2000);
            }
        }
    }

    private void scrollToTop() {
        uiComponents.getMainScrollView().smoothScrollTo(0, 0);
    }

    public void openLogFile(LogsPath logsPath) {
        File logFile;
        try {
            logFile = StorageUtils.getLogFile(this, logsPath);
        } catch (LogFileCreationException e) {
            uiComponents.showToast(getString(R.string.cannot_open_log_file));
            return;
        }

        if (logFile.exists()) {
            Uri fileUri =
                    FileProvider.getUriForFile(
                            this, "com.vhark.sftp_synchronizer.fileprovider", logFile);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setDataAndType(fileUri, "text/plain");

            if (getPackageManager().queryIntentActivities(intent, 0).isEmpty()) {
                intent.setDataAndType(fileUri, "*/*");
            }

            Intent chooser = Intent.createChooser(intent, getString(R.string.open_log_file_with));
            startActivity(chooser);
        } else {
            uiComponents.showToast(getString(R.string.log_file_not_exist));
        }
    }

    public void resetChooseButtonsState(View view) {
        if (isVisible(uiComponents.getChooseDirectoryButton())
                || isVisible(uiComponents.getChooseFileButton())) {
            uiComponents.animateFileButtonBack();
        }
    }

    private void resetChooseButtonsState() {
        if (isVisible(uiComponents.getChooseDirectoryButton())
                || isVisible(uiComponents.getChooseFileButton())) {
            uiComponents.animateFileButtonBack();
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

    private void synchronize() {
        if (uiComponents.isSyncButtonDisabled()) {
            handleSyncButtonDisabled();
            return;
        }

        initializeSynchronizationUI();

        new Thread(this::performSynchronization).start();
    }

    private void handleSyncButtonDisabled() {
        if (isOldPermissionVersion() && !oldPermissionsGranted()) {
            uiComponents.showToast(getString(R.string.permission_for_storage_should_granted));
            return;
        }
        if (!isSftpCredentialsFilled()) {
            uiComponents.showToast(getString(R.string.sftp_credentials_should_filled));
            return;
        }

        if (uiComponents.getPathPhoneInput().getText().length() == 0
                || uiComponents.getPathSftpInput().getText().length() == 0) {
            uiComponents.showToast(getString(R.string.paths_cant_be_empty));
            return;
        }

        uiComponents.showToast(getString(R.string.paths_must_have_same_target));
    }

    private void initializeSynchronizationUI() {
        cancelRequested = false;
        uiComponents.setOverlayViewVisible();
    }

    private void performSynchronization() {
        SftpStorage sftpStorage = null;

        try {
            PhoneStorage phoneStorage = initializePhoneStorage();
            sftpStorage = initializeSftpStorage();

            phoneStorage.setOperationExecutionListener(this);
            sftpStorage.setOperationExecutionListener(this);

            synchronizeFiles(phoneStorage, sftpStorage);

            if (!cancelRequested) {
                finalizeSynchronization(phoneStorage, sftpStorage);
            }
        } catch (Exception e) {
            handleSynchronizationException(e);
        } finally {
            if (sftpStorage != null) {
                sftpStorage.closeConnection();
            }
            finalizeSynchronizationUI();
        }
    }

    private PhoneStorage initializePhoneStorage() throws InvalidTargetFilePathException {
        Path targetPhonePath = Paths.get(loadStoredStringValue(KEY_PHONE_PATH));
        PhoneStorage phoneStorage = new PhoneStorage(this, targetPhonePath);
        String mergePath = phoneStorage.getMergePath();
        runOnUiThread(
                () ->
                        uiComponents
                                .getOverlayTextView()
                                .setText(getString(R.string.merge_path_message, mergePath)));
        return phoneStorage;
    }

    private SftpStorage initializeSftpStorage()
            throws InvalidSftpCredentialsException, SftpException, InvalidTargetFilePathException {
        Path targetPhonePath = Paths.get(loadStoredStringValue(KEY_PHONE_PATH));
        Path targetSftpPath = Paths.get(loadStoredStringValue(KEY_SFTP_PATH));
        String sftpAddress = loadStoredStringValue(KEY_SFTP_ADDRESS);
        String username = loadStoredStringValue(KEY_USERNAME);
        String password = loadStoredStringValue(KEY_PASSWORD);

        onOperationExecute(getString(R.string.connecting_to_sftp));
        SftpStorage sftpStorage =
                new SftpStorage(
                        this, targetSftpPath, targetPhonePath, sftpAddress, username, password);
        onOperationExecute(getString(R.string.success_connecting_to_sftp));

        return sftpStorage;
    }

    private void synchronizeFiles(PhoneStorage phoneStorage, SftpStorage sftpStorage)
            throws Exception {
        Path targetPhonePath = phoneStorage.getTargetPath();
        Path targetSftpPath = sftpStorage.getTargetPath();

        validatePaths(targetPhonePath, targetSftpPath, sftpStorage);

        onOperationExecute(getString(R.string.reading_logs));
        List<String> phoneLoggedStructure = StorageUtils.readLogs(this, PHONE_LOG_TAG);
        List<String> sftpLoggedStructure = StorageUtils.readLogs(this, SFTP_LOG_TAG);
        String loggedMergePath = StorageUtils.getLoggedMergePath(this);
        onOperationExecute(getString(R.string.success_logs_read));

        if (!Files.isDirectory(targetPhonePath)) {
            handleFileSynchronization(phoneStorage, sftpStorage, targetPhonePath);
        } else {
            handleDirectorySynchronization(
                    phoneStorage,
                    sftpStorage,
                    targetPhonePath,
                    targetSftpPath,
                    phoneLoggedStructure,
                    sftpLoggedStructure,
                    loggedMergePath);
        }
    }

    private void validatePaths(Path targetPhonePath, Path targetSftpPath, SftpStorage sftpStorage)
            throws BothPathsNotExistException, IncompatiblePathException, SftpException {
        boolean isPhoneTargetPathExists = Files.exists(targetPhonePath);
        boolean isSftpTargetPathExists = sftpStorage.exists(targetSftpPath.toString());

        if (!isPhoneTargetPathExists && !isSftpTargetPathExists) {
            throw new BothPathsNotExistException(this);
        }

        if (isPhoneTargetPathExists && isSftpTargetPathExists) {
            boolean isPhoneTargetPathDirectory = Files.isDirectory(targetPhonePath);
            boolean isSftpTargetPathDirectory =
                    sftpStorage.getAttributes(targetSftpPath.toString()).isDir();

            if (isPhoneTargetPathDirectory != isSftpTargetPathDirectory) {
                throw new IncompatiblePathException(this);
            }
        }
    }

    private void handleFileSynchronization(
            PhoneStorage phoneStorage, SftpStorage sftpStorage, Path targetPhonePath)
            throws Exception {
        onOperationExecute(getString(R.string.two_files_mod));

        boolean newFileCreated = StorageUtils.copyNewFiles(phoneStorage, sftpStorage);

        if (!newFileCreated) {
            Map<String, String> copyFiles =
                    StorageUtils.synchronizeFiles(phoneStorage, sftpStorage);

            if (copyFiles.isEmpty()) {
                onOperationExecute(getString(R.string.file_up_to_date));
            } else {
                handleFileCopying(copyFiles, targetPhonePath, phoneStorage, sftpStorage);
            }
        }
    }

    private void handleFileCopying(
            Map<String, String> copyFiles,
            Path targetPhonePath,
            PhoneStorage phoneStorage,
            SftpStorage sftpStorage)
            throws InterruptedException, SftpException, IOException {
        boolean copyFromPhone = copyFiles.containsKey(targetPhonePath.toString());

        if (!uiComponents.getAutoConfirmCheckBox().isChecked()) {
            String replaceTitle = getString(R.string.replace_title);
            String replaceMessage =
                    StorageUtils.getReplaceFileMessage(this, copyFiles, copyFromPhone);

            CountDownLatch latch = new CountDownLatch(1);
            showConfirmationDialog(replaceTitle, replaceMessage, latch);
            latch.await();

            if (cancelRequested) return;
        }

        if (copyFromPhone) {
            sftpStorage.copyFilesFromPhone(copyFiles, false);
        } else {
            phoneStorage.copyFilesFromSftp(copyFiles, false, sftpStorage);
        }
    }

    private void handleDirectorySynchronization(
            PhoneStorage phoneStorage,
            SftpStorage sftpStorage,
            Path targetPhonePath,
            Path targetSftpPath,
            List<String> phoneLoggedStructure,
            List<String> sftpLoggedStructure,
            String loggedMergePath)
            throws Exception {
        if (!targetPhonePath.getFileName().equals(targetSftpPath.getFileName())) {
            throw new DifferentDirectoriesNamesException(this);
        }

        String mergePath = phoneStorage.getMergePath();

        onOperationExecute(getString(R.string.getting_current_storage_structures));
        List<String> phoneStructure = phoneStorage.getLoggedStructure(targetPhonePath, false);
        List<String> sftpStructure = sftpStorage.getLoggedStructure(targetSftpPath, false);

        boolean isMergePathChange = !mergePath.equals(loggedMergePath);

        handleNewFiles(
                phoneStorage,
                sftpStorage,
                phoneStructure,
                sftpStructure,
                phoneLoggedStructure,
                sftpLoggedStructure,
                isMergePathChange);

        onOperationExecute(getString(R.string.checking_for_deleted_files));
        if (PrefsConstants.instance().fetchValueBoolean(KEY_DELETE_FUNCTION_DISABLED)) {
            onOperationExecute(getString(R.string.skipped_deleting_disabled));
        } else if (!mergePath.equals(loggedMergePath)) {
            onOperationExecute(getString(R.string.skipped_unable_to_identify_deleted_files));
        } else {
            handleDeletedFiles(
                    phoneStorage,
                    sftpStorage,
                    phoneStructure,
                    sftpStructure,
                    phoneLoggedStructure,
                    sftpLoggedStructure);

            if (cancelRequested) return;
        }

        handleFileUpdates(phoneStorage, sftpStorage, phoneStructure, sftpStructure);
    }

    private void handleNewFiles(
            PhoneStorage phoneStorage,
            SftpStorage sftpStorage,
            List<String> phoneStructure,
            List<String> sftpStructure,
            List<String> phoneLoggedStructure,
            List<String> sftpLoggedStructure,
            boolean isMergePathChange)
            throws IOException, SftpException {
        onOperationExecute(getString(R.string.checking_for_new_files));

        Path sftpStoragePath = sftpStorage.getStorageDirectoryPath();
        Path phoneStoragePath = phoneStorage.getStorageDirectoryPath();
        Map<String, String> newFilesToCopyFromSftpToPhone;
        Map<String, String> newFilesToCopyFromPhoneToSftp;

        if (isMergePathChange) {
            newFilesToCopyFromSftpToPhone =
                    StorageUtils.getPathsForCopyingNewFilesByComparison(
                            sftpStructure, phoneStructure, sftpStoragePath, phoneStoragePath);
            newFilesToCopyFromPhoneToSftp =
                    StorageUtils.getPathsForCopyingNewFilesByComparison(
                            phoneStructure, sftpStructure, phoneStoragePath, sftpStoragePath);
        } else {
            newFilesToCopyFromSftpToPhone =
                    StorageUtils.getPathsForCopyingNewFilesByLogs(
                            sftpStructure, sftpLoggedStructure, sftpStoragePath, phoneStoragePath);
            newFilesToCopyFromPhoneToSftp =
                    StorageUtils.getPathsForCopyingNewFilesByLogs(
                            phoneStructure,
                            phoneLoggedStructure,
                            phoneStoragePath,
                            sftpStoragePath);
        }

        List<String> createdFilesOnPhone =
                phoneStorage.copyFilesFromSftp(newFilesToCopyFromSftpToPhone, true, sftpStorage);
        List<String> createdFilesOnSftp =
                sftpStorage.copyFilesFromPhone(newFilesToCopyFromPhoneToSftp, true);

        phoneStructure.addAll(createdFilesOnPhone);
        sftpStructure.addAll(createdFilesOnSftp);

        if (createdFilesOnPhone.isEmpty() && createdFilesOnSftp.isEmpty()) {
            onOperationExecute(getString(R.string.no_new_files));
        }
    }

    private void handleDeletedFiles(
            PhoneStorage phoneStorage,
            SftpStorage sftpStorage,
            List<String> phoneStructure,
            List<String> sftpStructure,
            List<String> phoneLoggedStructure,
            List<String> sftpLoggedStructure)
            throws IOException, SftpException, InterruptedException {

        List<String> pathsForDeletingOnPhone =
                StorageUtils.getPathsForDeleting(
                        sftpStructure,
                        sftpLoggedStructure,
                        sftpStorage.getStorageDirectoryPath(),
                        phoneStorage.getStorageDirectoryPath());
        List<String> pathsForDeletingOnSftp =
                StorageUtils.getPathsForDeleting(
                        phoneStructure,
                        phoneLoggedStructure,
                        phoneStorage.getStorageDirectoryPath(),
                        sftpStorage.getStorageDirectoryPath());

        if (!pathsForDeletingOnPhone.isEmpty() || !pathsForDeletingOnSftp.isEmpty()) {

            if (!uiComponents.getAutoConfirmCheckBox().isChecked()) {
                String deleteTitle = getString(R.string.delete_title);
                String deleteMessage =
                        StorageUtils.getDeleteMessage(
                                this, pathsForDeletingOnPhone, pathsForDeletingOnSftp);

                CountDownLatch latch = new CountDownLatch(1);
                showConfirmationDialog(deleteTitle, deleteMessage, latch);
                latch.await();

                if (cancelRequested) return;
            }

            phoneStorage.deleteFiles(pathsForDeletingOnPhone);
            sftpStorage.deleteFiles(pathsForDeletingOnSftp);

            phoneStructure.removeAll(pathsForDeletingOnPhone);
            sftpStructure.removeAll(pathsForDeletingOnSftp);
        } else {
            onOperationExecute(getString(R.string.no_deleted_files));
        }
    }

    private void handleFileUpdates(
            PhoneStorage phoneStorage,
            SftpStorage sftpStorage,
            List<String> phoneStructure,
            List<String> sftpStructure)
            throws IOException, SftpException, InterruptedException {
        List<String> mergeStructure =
                StorageUtils.getMergeStructure(
                        phoneStructure,
                        sftpStructure,
                        phoneStorage.getStorageDirectoryPath(),
                        sftpStorage.getStorageDirectoryPath());

        onOperationExecute(getString(R.string.comparing_files));
        Map<String, String> allUpdatePaths =
                StorageUtils.getPathsForSynchronizingFiles(
                        mergeStructure, phoneStorage, sftpStorage);
        Map<String, String> filesToCopyFromSftpToPhone =
                StorageUtils.getSpecificUpdatePaths(
                        allUpdatePaths, sftpStorage.getStorageDirectoryPath());
        Map<String, String> filesToCopyFromPhoneToSftp =
                StorageUtils.getSpecificUpdatePaths(
                        allUpdatePaths, phoneStorage.getStorageDirectoryPath());

        if (!filesToCopyFromSftpToPhone.isEmpty() || !filesToCopyFromPhoneToSftp.isEmpty()) {

            if (!uiComponents.getAutoConfirmCheckBox().isChecked()) {
                String updateTitle = getString(R.string.update_title);
                String updateMessage =
                        StorageUtils.getUpdateMessage(
                                this, filesToCopyFromSftpToPhone, filesToCopyFromPhoneToSftp);

                CountDownLatch latch = new CountDownLatch(1);
                showConfirmationDialog(updateTitle, updateMessage, latch);
                latch.await();

                if (cancelRequested) return;
            }

            phoneStorage.copyFilesFromSftp(filesToCopyFromSftpToPhone, false, sftpStorage);
            sftpStorage.copyFilesFromPhone(filesToCopyFromPhoneToSftp, false);
        } else {
            onOperationExecute(getString(R.string.all_files_up_to_date));
        }
    }

    private void finalizeSynchronization(PhoneStorage phoneStorage, SftpStorage sftpStorage)
            throws LogFileCreationException, PathsLoggingException, IOException {
        onOperationExecute(getString(R.string.writing_logs));
        StorageUtils.clearLogFile(this);
        phoneStorage.writeLogs(this);
        sftpStorage.writeLogs(this);

        runOnUiThread(
                () -> {
                    uiComponents
                            .getOverlayTitleTextView()
                            .setText(getString(R.string.synchronization_successful));
                    uiComponents.showToast(getString(R.string.success));
                });
    }

    private void handleSynchronizationException(Exception e) {
        e.printStackTrace();
        runOnUiThread(
                () -> {
                    uiComponents.showToast(e.getMessage());
                    onOperationExecute("\n\n!!!&gt; " + e.getMessage());
                    uiComponents
                            .getOverlayTitleTextView()
                            .setText(getString(R.string.synchronization_failed));
                    uiComponents
                            .getOverlayTitleTextView()
                            .setBackgroundResource(R.drawable.overlay_items_error_background);
                    uiComponents
                            .getOverlayScrollView()
                            .setBackgroundResource(R.drawable.overlay_items_error_background);
                });
    }

    private void finalizeSynchronizationUI() {
        runOnUiThread(
                () -> {
                    if (uiComponents.getFadeLogoSynchronizingAnimation() != null) {
                        uiComponents.getFadeLogoSynchronizingAnimation().end();
                        uiComponents.getOverlayAppIconImageView().setAlpha(0f);
                    }

                    if (cancelRequested) {
                        uiComponents
                                .getOverlayTitleTextView()
                                .setBackgroundResource(R.drawable.overlay_items_error_background);
                        uiComponents
                                .getOverlayScrollView()
                                .setBackgroundResource(R.drawable.overlay_items_error_background);
                        uiComponents
                                .getOverlayTitleTextView()
                                .setText(getString(R.string.synchronization_canceled));
                        onOperationExecute(
                                "\n\n!!!&gt; " + getString(R.string.synchronization_canceled));
                    }

                    String operationsLog = uiComponents.getOverlayTextView().getText().toString();

                    try {
                        StorageUtils.writeOperationsLogFile(this, operationsLog);
                    } catch (LogFileCreationException e) {
                        uiComponents.showToast(getString(R.string.cant_save_operations_log));
                    }

                    uiComponents
                            .getOverlayTextView()
                            .setText(StorageUtils.convertOperationsLogs(this, operationsLog));

                    uiComponents.getOverlayProgressBar().setVisibility(View.GONE);
                    uiComponents.getOpenLogsButton().setVisibility(View.VISIBLE);
                    uiComponents.getCloseOverlayButton().setVisibility(View.VISIBLE);
                });
    }
}
