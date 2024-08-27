package com.vhark.sftp_synchronizer.storage;

import static com.vhark.sftp_synchronizer.constant.LogTags.MERGE_PATH_LOG_TAG;
import static com.vhark.sftp_synchronizer.constant.LogTags.PHONE_LOG_TAG;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;

import com.jcraft.jsch.SftpException;
import com.vhark.sftp_synchronizer.R;
import com.vhark.sftp_synchronizer.exception.InvalidTargetFilePathException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class PhoneStorage extends Storage {

    private final Path storageDirectoryPath = Environment.getExternalStorageDirectory().toPath();
    private final File targetFile;
    private final Context context;

    public PhoneStorage(Context context, Path targetPath) throws InvalidTargetFilePathException {
        this.context = context;
        File targetDirectory = targetPath.toFile();
        if (!targetDirectory.exists()) {
            if (targetDirectory.getParentFile() == null
                    || !targetDirectory.getParentFile().exists()) {
                throw new InvalidTargetFilePathException(context, targetPath);
            }
        }
        this.targetFile = targetDirectory;
    }

    @Override
    public Path getTargetPath() {
        return targetFile.toPath();
    }

    @Override
    public List<String> getLoggedStructure(Path path, boolean withTags) {
        notifyOperationExecuted(context.getString(R.string.reading_phone_storage));
        List<String> loggedStructure = new ArrayList<>();
        if (!Files.isDirectory(path)) {
            notifyOperationExecuted(
                    "\n\n   " + context.getString(R.string.read_operation) + " " + path);
            if (withTags) {
                loggedStructure.add(PHONE_LOG_TAG + path.toString());
            } else {
                loggedStructure.add(path.toString());
            }
        } else {
            loggedStructure = recursiveLogStructure(path, new LinkedList<>(), withTags);
            loggedStructure.remove(0);
        }
        if (withTags) {
            loggedStructure.add(0, MERGE_PATH_LOG_TAG + getMergePath());
        }
        return loggedStructure;
    }

    public List<String> recursiveLogStructure(
            Path directoryPath, List<String> log, boolean withTags) {
        File directory = directoryPath.toFile();
        notifyOperationExecuted(
                "\n\n   "
                        + context.getString(R.string.read_operation)
                        + " "
                        + directory.getAbsolutePath());
        if (withTags) {
            log.add(PHONE_LOG_TAG + directory.getAbsolutePath());
        } else {
            log.add(directory.getAbsolutePath());
        }

        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    recursiveLogStructure(file.toPath(), log, withTags);
                } else {
                    notifyOperationExecuted(
                            "\n\n   "
                                    + context.getString(R.string.read_operation)
                                    + " "
                                    + file.getAbsolutePath());
                    if (withTags) {
                        log.add(PHONE_LOG_TAG + file.getAbsolutePath());
                    } else {
                        log.add(file.getAbsolutePath());
                    }
                }
            }
        }

        return log;
    }

    public List<String> copyFilesFromSftp(
            Map<String, String> paths, boolean skipIfExists, SftpStorage sftpStorage)
            throws SftpException, IOException {

        if (!paths.isEmpty()) {
            notifyOperationExecuted(
                    context.getString(R.string.copying_files_dirs_from_sftp_to_phone));
        }

        List<String> createdFiles = new LinkedList<>();

        for (Map.Entry<String, String> entry : paths.entrySet()) {
            Path phonePath = Paths.get(entry.getValue());
            String sftpPath = entry.getKey();

            if (skipIfExists && Files.exists(phonePath)) {
                continue;
            }

            if (sftpStorage.getAttributes(sftpPath).isDir()) {
                Files.createDirectories(phonePath);
            } else {
                copyFileFromSftp(phonePath, sftpPath, sftpStorage);
            }

            createdFiles.add(entry.getValue());
        }

        return createdFiles;
    }

    public void copyFileFromSftp(Path phonePath, String sftpPath, SftpStorage sftpStorage)
            throws SftpException, IOException {
        Files.createDirectories(phonePath.getParent());

        notifyOperationExecuted(
                "\n\n   " + context.getString(R.string.download_operation) + " " + sftpPath);
        try (InputStream inputStream = sftpStorage.downloadFile(sftpPath);
                FileOutputStream outputStream = new FileOutputStream(phonePath.toFile())) {

            notifyOperationExecuted(
                    "\n\n   " + context.getString(R.string.insert_operation) + " " + phonePath);

            byte[] buffer = new byte[1024];
            int bytesRead;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }

            sftpStorage.updateModificationTime(sftpPath);
        }
    }

    public void copyFileFromSftp(Uri phoneUri, String sftpPath, SftpStorage sftpStorage)
            throws SftpException, IOException {

        notifyOperationExecuted(
                "\n\n   " + context.getString(R.string.download_operation) + " " + sftpPath);
        try (InputStream inputStream = sftpStorage.downloadFile(sftpPath);
                OutputStream outputStream =
                        context.getContentResolver().openOutputStream(phoneUri)) {

            notifyOperationExecuted(
                    "\n\n   " + context.getString(R.string.insert_operation) + " " + phoneUri);

            byte[] buffer = new byte[1024];
            int bytesRead;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }

            sftpStorage.updateModificationTime(sftpPath);
        }
    }

    @Override
    public void deleteFiles(List<String> pathsForDeleting) throws IOException {
        if (!pathsForDeleting.isEmpty()) {
            notifyOperationExecuted(context.getString(R.string.deleting_files_dirs_on_phone));
        }

        for (String pathString : pathsForDeleting) {
            Path path = Paths.get(pathString);
            if (Files.exists(path)) {
                deleteRecursively(path);
            }
        }
    }

    private void deleteRecursively(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (DirectoryStream<Path> entries = Files.newDirectoryStream(path)) {
                for (Path entry : entries) {
                    deleteRecursively(entry);
                }
            }
        }
        notifyOperationExecuted("\n\n   " + context.getString(R.string.del_operation) + " " + path);
        Files.delete(path);
    }

    @Override
    public Path getStorageDirectoryPath() {
        return storageDirectoryPath;
    }

    public String getMergePath() {
        return getTargetPath().toString().replaceFirst(storageDirectoryPath.toString(), "");
    }
}
