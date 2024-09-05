package com.vhark.sftp_synchronizer.handler;

import static com.vhark.sftp_synchronizer.constant.LogTags.PHONE_LOG_TAG;
import static com.vhark.sftp_synchronizer.constant.LogTags.SFTP_LOG_TAG;
import static com.vhark.sftp_synchronizer.constant.PrefsKeys.KEY_DELETE_FUNCTION_DISABLED;
import static com.vhark.sftp_synchronizer.constant.PrefsKeys.KEY_PASSWORD;
import static com.vhark.sftp_synchronizer.constant.PrefsKeys.KEY_PHONE_PATH;
import static com.vhark.sftp_synchronizer.constant.PrefsKeys.KEY_SFTP_ADDRESS;
import static com.vhark.sftp_synchronizer.constant.PrefsKeys.KEY_SFTP_PATH;
import static com.vhark.sftp_synchronizer.constant.PrefsKeys.KEY_USERNAME;

import android.view.View;

import com.jcraft.jsch.SftpException;
import com.vhark.sftp_synchronizer.MainActivity;
import com.vhark.sftp_synchronizer.R;
import com.vhark.sftp_synchronizer.UI.UIComponents;
import com.vhark.sftp_synchronizer.constant.PrefsConstants;
import com.vhark.sftp_synchronizer.exception.BothPathsNotExistException;
import com.vhark.sftp_synchronizer.exception.DifferentDirectoriesNamesException;
import com.vhark.sftp_synchronizer.exception.IncompatiblePathException;
import com.vhark.sftp_synchronizer.exception.InvalidSftpCredentialsException;
import com.vhark.sftp_synchronizer.exception.InvalidTargetFilePathException;
import com.vhark.sftp_synchronizer.exception.LogFileCreationException;
import com.vhark.sftp_synchronizer.exception.PathsLoggingException;
import com.vhark.sftp_synchronizer.storage.PhoneStorage;
import com.vhark.sftp_synchronizer.storage.SftpStorage;
import com.vhark.sftp_synchronizer.storage.StorageUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

public class SynchronizationHandler {

    private final MainActivity activity;
    private final UIComponents uiComponents;
    private final EventHandler eventHandler;

    public SynchronizationHandler(
            MainActivity activity, UIComponents uiComponents, EventHandler eventHandler) {
        this.activity = activity;
        this.uiComponents = uiComponents;
        this.eventHandler = eventHandler;
    }

    public void synchronize() {
        if (uiComponents.isSyncButtonDisabled()) {
            eventHandler.handleSyncButtonDisabled();
            return;
        }

        initializeSynchronizationUI();

        new Thread(this::performSynchronization).start();
    }

    private void initializeSynchronizationUI() {
        activity.setCancelRequested(false);
        uiComponents.setOverlayViewVisible();
    }

    private void performSynchronization() {
        SftpStorage sftpStorage = null;

        try {
            PhoneStorage phoneStorage = initializePhoneStorage();
            sftpStorage = initializeSftpStorage();

            phoneStorage.setOperationExecutionListener(activity);
            sftpStorage.setOperationExecutionListener(activity);

            synchronizeFiles(phoneStorage, sftpStorage);

            if (!activity.isCancelRequested()) {
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
        Path targetPhonePath =
                Paths.get(PrefsConstants.instance().fetchValueString(KEY_PHONE_PATH));
        PhoneStorage phoneStorage = new PhoneStorage(activity, targetPhonePath);
        String mergePath = phoneStorage.getMergePath();
        activity.runOnUiThread(
                () ->
                        uiComponents
                                .getOverlayTextView()
                                .setText(
                                        activity.getString(
                                                R.string.merge_path_message, mergePath)));
        return phoneStorage;
    }

    private SftpStorage initializeSftpStorage()
            throws InvalidSftpCredentialsException, SftpException, InvalidTargetFilePathException {
        Path targetPhonePath =
                Paths.get(PrefsConstants.instance().fetchValueString(KEY_PHONE_PATH));
        Path targetSftpPath = Paths.get(PrefsConstants.instance().fetchValueString(KEY_SFTP_PATH));
        String sftpAddress = PrefsConstants.instance().fetchValueString(KEY_SFTP_ADDRESS);
        String username = PrefsConstants.instance().fetchValueString(KEY_USERNAME);
        String password = PrefsConstants.instance().fetchValueString(KEY_PASSWORD);

        activity.onOperationExecute(activity.getString(R.string.connecting_to_sftp));
        SftpStorage sftpStorage =
                new SftpStorage(
                        activity, targetSftpPath, targetPhonePath, sftpAddress, username, password);
        activity.onOperationExecute(activity.getString(R.string.success_connecting_to_sftp));

        return sftpStorage;
    }

    private void synchronizeFiles(PhoneStorage phoneStorage, SftpStorage sftpStorage)
            throws Exception {
        Path targetPhonePath = phoneStorage.getTargetPath();
        Path targetSftpPath = sftpStorage.getTargetPath();

        validatePaths(targetPhonePath, targetSftpPath, sftpStorage);

        activity.onOperationExecute(activity.getString(R.string.reading_logs));
        List<String> phoneLoggedStructure = StorageUtils.readLogs(activity, PHONE_LOG_TAG);
        List<String> sftpLoggedStructure = StorageUtils.readLogs(activity, SFTP_LOG_TAG);
        String loggedMergePath = StorageUtils.getLoggedMergePath(activity);
        activity.onOperationExecute(activity.getString(R.string.success_logs_read));

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
            throw new BothPathsNotExistException(activity);
        }

        if (isPhoneTargetPathExists && isSftpTargetPathExists) {
            boolean isPhoneTargetPathDirectory = Files.isDirectory(targetPhonePath);
            boolean isSftpTargetPathDirectory =
                    sftpStorage.getAttributes(targetSftpPath.toString()).isDir();

            if (isPhoneTargetPathDirectory != isSftpTargetPathDirectory) {
                throw new IncompatiblePathException(activity);
            }
        }
    }

    private void handleFileSynchronization(
            PhoneStorage phoneStorage, SftpStorage sftpStorage, Path targetPhonePath)
            throws Exception {
        activity.onOperationExecute(activity.getString(R.string.two_files_mod));

        boolean newFileCreated = StorageUtils.copyNewFiles(phoneStorage, sftpStorage);

        if (!newFileCreated) {
            Map<String, String> copyFiles =
                    StorageUtils.synchronizeFiles(phoneStorage, sftpStorage);

            if (copyFiles.isEmpty()) {
                activity.onOperationExecute(activity.getString(R.string.file_up_to_date));
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
            String replaceTitle = activity.getString(R.string.replace_title);
            String replaceMessage =
                    StorageUtils.getReplaceFileMessage(activity, copyFiles, copyFromPhone);

            CountDownLatch latch = new CountDownLatch(1);
            uiComponents.showConfirmationDialog(replaceTitle, replaceMessage, latch);
            latch.await();

            if (activity.isCancelRequested()) return;
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
            throw new DifferentDirectoriesNamesException(activity);
        }

        String mergePath = phoneStorage.getMergePath();

        activity.onOperationExecute(
                activity.getString(R.string.getting_current_storage_structures));
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

        activity.onOperationExecute(activity.getString(R.string.checking_for_deleted_files));
        if (PrefsConstants.instance().fetchValueBoolean(KEY_DELETE_FUNCTION_DISABLED)) {
            activity.onOperationExecute(activity.getString(R.string.skipped_deleting_disabled));
        } else if (!mergePath.equals(loggedMergePath)) {
            activity.onOperationExecute(
                    activity.getString(R.string.skipped_unable_to_identify_deleted_files));
        } else {
            handleDeletedFiles(
                    phoneStorage,
                    sftpStorage,
                    phoneStructure,
                    sftpStructure,
                    phoneLoggedStructure,
                    sftpLoggedStructure);

            if (activity.isCancelRequested()) return;
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
        activity.onOperationExecute(activity.getString(R.string.checking_for_new_files));

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
            activity.onOperationExecute(activity.getString(R.string.no_new_files));
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
                String deleteTitle = activity.getString(R.string.delete_title);
                String deleteMessage =
                        StorageUtils.getDeleteMessage(
                                activity, pathsForDeletingOnPhone, pathsForDeletingOnSftp);

                CountDownLatch latch = new CountDownLatch(1);
                uiComponents.showConfirmationDialog(deleteTitle, deleteMessage, latch);
                latch.await();

                if (activity.isCancelRequested()) return;
            }

            phoneStorage.deleteFiles(pathsForDeletingOnPhone);
            sftpStorage.deleteFiles(pathsForDeletingOnSftp);

            phoneStructure.removeAll(pathsForDeletingOnPhone);
            sftpStructure.removeAll(pathsForDeletingOnSftp);
        } else {
            activity.onOperationExecute(activity.getString(R.string.no_deleted_files));
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

        activity.onOperationExecute(activity.getString(R.string.comparing_files));
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
                String updateTitle = activity.getString(R.string.update_title);
                String updateMessage =
                        StorageUtils.getUpdateMessage(
                                activity, filesToCopyFromSftpToPhone, filesToCopyFromPhoneToSftp);

                CountDownLatch latch = new CountDownLatch(1);
                uiComponents.showConfirmationDialog(updateTitle, updateMessage, latch);
                latch.await();

                if (activity.isCancelRequested()) return;
            }

            phoneStorage.copyFilesFromSftp(filesToCopyFromSftpToPhone, false, sftpStorage);
            sftpStorage.copyFilesFromPhone(filesToCopyFromPhoneToSftp, false);
        } else {
            activity.onOperationExecute(activity.getString(R.string.all_files_up_to_date));
        }
    }

    private void finalizeSynchronization(PhoneStorage phoneStorage, SftpStorage sftpStorage)
            throws LogFileCreationException, PathsLoggingException, IOException {
        activity.onOperationExecute(activity.getString(R.string.writing_logs));
        StorageUtils.clearLogFile(activity);
        phoneStorage.writeLogs(activity);
        sftpStorage.writeLogs(activity);

        activity.runOnUiThread(
                () -> {
                    uiComponents
                            .getOverlayTitleTextView()
                            .setText(activity.getString(R.string.synchronization_successful));
                    uiComponents.showToast(activity.getString(R.string.success));
                });
    }

    private void handleSynchronizationException(Exception e) {
        activity.runOnUiThread(
                () -> {
                    uiComponents.showToast(e.getMessage());
                    activity.onOperationExecute("\n\n!!!&gt; " + e.getMessage());
                    uiComponents
                            .getOverlayTitleTextView()
                            .setText(activity.getString(R.string.synchronization_failed));
                    uiComponents
                            .getOverlayTitleTextView()
                            .setBackgroundResource(R.drawable.overlay_items_error_background);
                    uiComponents
                            .getOverlayScrollView()
                            .setBackgroundResource(R.drawable.overlay_items_error_background);
                });
    }

    private void finalizeSynchronizationUI() {
        activity.runOnUiThread(
                () -> {
                    if (uiComponents.getFadeLogoSynchronizingAnimation() != null) {
                        uiComponents.getFadeLogoSynchronizingAnimation().end();
                        uiComponents.getOverlayAppIconImageView().setAlpha(0f);
                    }

                    if (activity.isCancelRequested()) {
                        uiComponents
                                .getOverlayTitleTextView()
                                .setBackgroundResource(R.drawable.overlay_items_error_background);
                        uiComponents
                                .getOverlayScrollView()
                                .setBackgroundResource(R.drawable.overlay_items_error_background);
                        uiComponents
                                .getOverlayTitleTextView()
                                .setText(activity.getString(R.string.synchronization_canceled));
                        activity.onOperationExecute(
                                "\n\n!!!&gt; "
                                        + activity.getString(R.string.synchronization_canceled));
                    }

                    String operationsLog = uiComponents.getOverlayTextView().getText().toString();

                    try {
                        StorageUtils.writeOperationsLogFile(activity, operationsLog);
                    } catch (LogFileCreationException e) {
                        uiComponents.showToast(
                                activity.getString(R.string.cant_save_operations_log));
                    }

                    uiComponents
                            .getOverlayTextView()
                            .setText(StorageUtils.convertOperationsLogs(activity, operationsLog));

                    uiComponents.getOverlayProgressBar().setVisibility(View.GONE);
                    uiComponents.getOpenLogsButton().setVisibility(View.VISIBLE);
                    uiComponents.getCloseOverlayButton().setVisibility(View.VISIBLE);
                });
    }
}
