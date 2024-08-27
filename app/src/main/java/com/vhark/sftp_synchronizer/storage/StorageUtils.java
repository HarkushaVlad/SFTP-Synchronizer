package com.vhark.sftp_synchronizer.storage;

import static com.vhark.sftp_synchronizer.constant.LogTags.MERGE_PATH_LOG_TAG;

import android.content.Context;
import android.text.Html;
import android.text.Spanned;

import com.jcraft.jsch.SftpException;
import com.vhark.sftp_synchronizer.R;
import com.vhark.sftp_synchronizer.constant.LogTags;
import com.vhark.sftp_synchronizer.constant.LogsPath;
import com.vhark.sftp_synchronizer.exception.LogFileCreationException;
import com.vhark.sftp_synchronizer.exception.LogsReadingException;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class StorageUtils {

    private StorageUtils() {}

    public static File getLogFile(Context context, LogsPath logsPath)
            throws LogFileCreationException {
        File logFile = new File(context.getFilesDir(), logsPath.toString());

        if (!logFile.exists()) {
            File parentDir = logFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            try {
                logFile.createNewFile();
            } catch (IOException e) {
                throw new LogFileCreationException(context, logsPath.toString());
            }
        }

        return logFile;
    }

    public static void writeOperationsLogFile(Context context, String operations)
            throws LogFileCreationException {
        File logFile = new File(context.getFilesDir(), LogsPath.OPERATIONS.toString());

        try {
            if (!logFile.exists()) {
                File parentDir = logFile.getParentFile();
                if (parentDir != null && !parentDir.exists()) {
                    parentDir.mkdirs();
                }
                logFile.createNewFile();
            }

            try (BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(logFile))) {
                String[] lines = operations.split("\n");
                for (String line : lines) {
                    bufferedWriter.write(line);
                    bufferedWriter.newLine();
                }
                bufferedWriter.flush();
            }
        } catch (IOException e) {
            throw new LogFileCreationException(context, LogsPath.OPERATIONS.toString());
        }
    }

    public static List<String> readLogs(Context context, LogTags logTag)
            throws FileNotFoundException, LogsReadingException, LogFileCreationException {
        FileReader fileReader = new FileReader(getLogFile(context, LogsPath.LOGS));

        try (BufferedReader bufferedReader = new BufferedReader(fileReader)) {
            List<String> logsList = new LinkedList<>();
            int startIndex = logTag.toString().length();

            String line = bufferedReader.readLine();

            while (line != null) {
                if (line.startsWith(logTag.toString())) {
                    logsList.add(line.substring(startIndex));
                }
                line = bufferedReader.readLine();
            }

            return logsList;
        } catch (IOException e) {
            throw new LogsReadingException(context);
        }
    }

    public static String getLoggedMergePath(Context context)
            throws LogFileCreationException, FileNotFoundException, LogsReadingException {
        String mergePath = "";
        FileReader fileReader = new FileReader(getLogFile(context, LogsPath.LOGS));

        try (BufferedReader bufferedReader = new BufferedReader(fileReader)) {
            int startIndex = MERGE_PATH_LOG_TAG.toString().length();

            String line = bufferedReader.readLine();

            while (line != null) {
                if (line.startsWith(MERGE_PATH_LOG_TAG.toString())) {
                    mergePath = line.substring(startIndex);
                }
                line = bufferedReader.readLine();
            }

            return mergePath;
        } catch (IOException e) {
            throw new LogsReadingException(context);
        }
    }

    public static void clearLogFile(Context context) throws LogFileCreationException, IOException {
        BufferedWriter bufferedEraser =
                new BufferedWriter(new FileWriter(getLogFile(context, LogsPath.LOGS)));
        bufferedEraser.flush();
    }

    public static Map<String, String> getPathsForCopyingNewFilesByComparison(
            List<String> fromStructure,
            List<String> toStructure,
            Path fromStoragePath,
            Path toStoragePath) {
        String fromPath = fromStoragePath.toString();
        String toPath = toStoragePath.toString();

        List<String> fromPlainPaths =
                fromStructure.stream()
                        .map(path -> path.replaceFirst(fromPath, ""))
                        .collect(Collectors.toList());
        List<String> toPlainPaths =
                toStructure.stream()
                        .map(path -> path.replaceFirst(toPath, ""))
                        .collect(Collectors.toList());

        Map<String, String> map = new HashMap<>();

        fromPlainPaths.stream()
                .filter(plainPath -> !toPlainPaths.contains(plainPath))
                .forEach(
                        plainPath -> {
                            map.put(fromStoragePath + plainPath, toStoragePath + plainPath);
                        });

        return map;
    }

    public static Map<String, String> getPathsForCopyingNewFilesByLogs(
            List<String> currentStructure,
            List<String> loggedStructure,
            Path originalStoragePath,
            Path destinationStoragePath) {
        String originalPath = originalStoragePath.toString();
        String destinationPath = destinationStoragePath.toString();
        Map<String, String> map = new HashMap<>();

        currentStructure.stream()
                .filter(path -> !loggedStructure.contains(path))
                .forEach(
                        path -> {
                            map.put(path, path.replaceFirst(originalPath, destinationPath));
                        });

        return map;
    }

    public static List<String> getPathsForDeleting(
            List<String> currentStructure,
            List<String> loggedStructure,
            Path originalStoragePath,
            Path destinationStoragePath) {
        String originalPath = originalStoragePath.toString();
        String destinationPath = destinationStoragePath.toString();

        return loggedStructure.stream()
                .filter(path -> !currentStructure.contains(path))
                .map(path -> path.replaceFirst(originalPath, destinationPath))
                .collect(Collectors.toList());
    }

    public static List<String> getMergeStructure(
            List<String> firstStructure,
            List<String> secondStructure,
            Path firstStorageDirectoryPath,
            Path secondStorageDirectoryPath) {
        String firstStorageDirectoryPathStr = firstStorageDirectoryPath.toString();
        String secondStorageDirectoryPathStr = secondStorageDirectoryPath.toString();

        List<String> plainFirstStructure =
                firstStructure.stream()
                        .map(path -> path.replaceFirst(firstStorageDirectoryPathStr, ""))
                        .collect(Collectors.toList());
        List<String> plainSecondStructure =
                secondStructure.stream()
                        .map(path -> path.replaceFirst(secondStorageDirectoryPathStr, ""))
                        .collect(Collectors.toList());

        Set<String> mergeStructure = new HashSet<>();
        mergeStructure.addAll(plainFirstStructure);
        mergeStructure.addAll(plainSecondStructure);

        return new ArrayList<>(mergeStructure);
    }

    public static Map<String, String> getPathsForSynchronizingFiles(
            List<String> mergeStructure, PhoneStorage phoneStorage, SftpStorage sftpStorage)
            throws IOException, SftpException {
        String phoneStorageDirectoryPath = phoneStorage.getStorageDirectoryPath().toString();
        String sftpStorageDirectoryPath = sftpStorage.getStorageDirectoryPath().toString();
        Map<String, String> allUpdatePaths = new HashMap<>();

        for (String mergePath : mergeStructure) {
            Path phonePath = Paths.get(phoneStorageDirectoryPath, mergePath);
            if (Files.isDirectory(phonePath)) {
                continue;
            }

            Instant phoneInstant = Files.getLastModifiedTime(phonePath).toInstant();

            String sftpPath = sftpStorageDirectoryPath + mergePath;
            Instant sftpInstant = getSftpModifiedTime(sftpStorage, sftpPath);

            Duration tolerance = Duration.ofSeconds(30);
            if (Duration.between(sftpInstant, phoneInstant).abs().compareTo(tolerance) > 0) {
                if (phoneInstant.isAfter(sftpInstant)) {
                    allUpdatePaths.put(phonePath.toString(), sftpPath);
                } else {
                    allUpdatePaths.put(sftpPath, phonePath.toString());
                }
            }
        }

        return allUpdatePaths;
    }

    public static Map<String, String> getSpecificUpdatePaths(
            Map<String, String> allUpdatePaths, Path sourceStorageDirectoryPath) {
        Map<String, String> resultMap = new HashMap<>();
        String sourceStorageDirectoryPathStr = sourceStorageDirectoryPath.toString();

        for (Map.Entry<String, String> entry : allUpdatePaths.entrySet()) {
            if (entry.getKey().contains(sourceStorageDirectoryPathStr)) {
                resultMap.put(entry.getKey(), entry.getValue());
            }
        }

        return resultMap;
    }

    public static boolean copyNewFiles(PhoneStorage phoneStorage, SftpStorage sftpStorage)
            throws SftpException, IOException {
        Path phonePath = phoneStorage.getTargetPath();
        String sftpPath = sftpStorage.getTargetPath().toString();

        boolean isPhonePathExists = Files.exists(phonePath);
        boolean isSftpPathExists = sftpStorage.exists(sftpPath);

        if (isPhonePathExists && !isSftpPathExists) {
            sftpStorage.copyFileFromPhone(phonePath, sftpPath);
            return true;
        }

        if (!isPhonePathExists && isSftpPathExists) {
            phoneStorage.copyFileFromSftp(phonePath, sftpPath, sftpStorage);
            return true;
        }

        return false;
    }

    public static Map<String, String> synchronizeFiles(
            PhoneStorage phoneStorage, SftpStorage sftpStorage) throws IOException, SftpException {
        Map<String, String> copyFiles = new HashMap<>();
        Path phonePath = phoneStorage.getTargetPath();
        String sftpPath = sftpStorage.getTargetPath().toString();

        Instant phoneInstant = Files.getLastModifiedTime(phonePath).toInstant();
        Instant sftpInstant = getSftpModifiedTime(sftpStorage, sftpPath);

        Duration tolerance = Duration.ofSeconds(30);
        if (Duration.between(sftpInstant, phoneInstant).abs().compareTo(tolerance) > 0) {
            if (phoneInstant.isAfter(sftpInstant)) {
                copyFiles.put(phonePath.toString(), sftpPath);
                return copyFiles;
            }
            if (phoneInstant.isBefore(sftpInstant)) {
                copyFiles.put(sftpPath, phonePath.toString());
                return copyFiles;
            }
        }

        return copyFiles;
    }

    private static Instant getSftpModifiedTime(SftpStorage sftpStorage, String sftpPath)
            throws SftpException {
        long sftpMTimeInMillis = sftpStorage.getAttributes(sftpPath).getMTime() * 1000L;
        return Instant.ofEpochMilli(sftpMTimeInMillis);
    }

    public static String getDeleteMessage(
            Context context,
            List<String> pathsForDeletingOnPhone,
            List<String> pathsForDeletingOnSftp) {

        StringBuilder result = new StringBuilder();

        appendDeletePaths(
                context, result, pathsForDeletingOnPhone, context.getString(R.string.phone));
        appendDeletePaths(
                context, result, pathsForDeletingOnSftp, context.getString(R.string.sftp));

        return result.toString();
    }

    private static void appendDeletePaths(
            Context context, StringBuilder result, List<String> paths, String platform) {
        if (!paths.isEmpty()) {
            result.append(context.getString(R.string.files_to_be_deleted, platform));
            for (String path : paths) {
                result.append(path).append("<br>");
            }
            result.append("<br>");
        }
    }

    public static String getUpdateMessage(
            Context context,
            Map<String, String> filesToCopyFromSftpToPhone,
            Map<String, String> filesToCopyFromPhoneToSftp) {
        String phone = context.getString(R.string.phone);
        String sftp = context.getString(R.string.sftp);
        StringBuilder result = new StringBuilder();

        appendFileUpdates(context, result, filesToCopyFromSftpToPhone, sftp, phone);
        appendFileUpdates(context, result, filesToCopyFromPhoneToSftp, phone, sftp);

        return result.toString();
    }

    private static void appendFileUpdates(
            Context context,
            StringBuilder result,
            Map<String, String> filesMap,
            String fromPlatform,
            String toPlatform) {
        if (!filesMap.isEmpty()) {
            result.append(context.getString(R.string.files_replaced_by, toPlatform, fromPlatform));
            for (Map.Entry<String, String> entry : filesMap.entrySet()) {
                result.append(
                        context.getString(
                                R.string.file_replace_format,
                                fromPlatform,
                                entry.getKey(),
                                toPlatform,
                                entry.getValue()));
            }
        }
    }

    public static String getReplaceFileMessage(
            Context context, Map<String, String> copyFiles, boolean copyFromPhone) {
        String phone = context.getString(R.string.phone);
        phone = phone.substring(0, 1).toUpperCase() + phone.substring(1);
        String sftp = context.getString(R.string.sftp);

        String fromPlatform = copyFromPhone ? phone : sftp;
        String toPlatform = copyFromPhone ? sftp : phone;

        return buildFileReplaceMessage(context, copyFiles, fromPlatform, toPlatform);
    }

    private static String buildFileReplaceMessage(
            Context context,
            Map<String, String> copyFiles,
            String fromPlatform,
            String toPlatform) {
        StringBuilder result = new StringBuilder();

        for (Map.Entry<String, String> entry : copyFiles.entrySet()) {
            result.append(
                    context.getString(
                            R.string.file_replace_format,
                            fromPlatform,
                            entry.getKey(),
                            toPlatform,
                            entry.getValue()));
        }

        return result.toString();
    }

    public static Spanned convertOperationsLogs(Context context, String logs) {
        String formattedLogs = logs.replaceAll(" ", "&nbsp;");
        String[] lines = formattedLogs.split("\n");

        StringBuilder result = new StringBuilder();

        Map<String, String> colorMap =
                Map.of(
                        context.getString(R.string.upload_operation), "#ffa500",
                        context.getString(R.string.create_dir_operation), "#ffa500",
                        context.getString(R.string.copy_operation), "#ffa500",
                        context.getString(R.string.download_operation), "#ffa500",
                        context.getString(R.string.insert_operation), "#ffa500",
                        context.getString(R.string.del_dir_operation), "red",
                        context.getString(R.string.del_file_operation), "red",
                        context.getString(R.string.del_operation), "red",
                        context.getString(R.string.read_operation), "green",
                        context.getString(R.string.write_operation), "yellow");

        for (String line : lines) {
            boolean matched = false;

            for (Map.Entry<String, String> entry : colorMap.entrySet()) {
                if (line.contains(entry.getKey())) {
                    int index = line.indexOf(">");
                    result.append("&lt;span style=&quot;color: ")
                            .append(entry.getValue())
                            .append("&quot;&gt;")
                            .append(line.substring(0, index + 1))
                            .append("&lt;/span&gt;")
                            .append(line.substring(index + 1))
                            .append("&lt;br&gt;");
                    matched = true;
                    break;
                }
            }

            if (!matched) {
                if (line.contains("@")) {
                    result.append("&lt;span style=&quot;color: #65CBB4;&quot;&gt;")
                            .append(line)
                            .append("&lt;/span&gt;");
                } else if (line.contains("!!!&gt;")) {
                    result.append("&lt;span style=&quot;color: red;&quot;&gt;")
                            .append(line)
                            .append("&lt;/span&gt;");
                } else {
                    result.append(line);
                }
                result.append("&lt;br&gt;");
            }
        }

        String plainMarkup =
                Html.fromHtml(result.toString(), Html.FROM_HTML_MODE_LEGACY).toString();
        return Html.fromHtml(plainMarkup, Html.FROM_HTML_MODE_LEGACY);
    }
}
