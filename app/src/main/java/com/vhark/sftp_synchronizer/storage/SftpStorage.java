package com.vhark.sftp_synchronizer.storage;

import static com.vhark.sftp_synchronizer.constant.LogTags.SFTP_LOG_TAG;

import android.content.Context;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;
import com.vhark.sftp_synchronizer.R;
import com.vhark.sftp_synchronizer.exception.InvalidSftpCredentialsException;
import com.vhark.sftp_synchronizer.exception.InvalidTargetFilePathException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TimeZone;
import java.util.Vector;

public class SftpStorage extends Storage {

    private final Path targetPath;
    private final Path storageDirectoryPath;
    private final String sftpAddress;
    private final int sftpPort;
    private final String sftpUsername;
    private final String sftpPassword;
    private final Context context;
    private ChannelSftp channelSftp;
    private Session session;

    public SftpStorage(
            Context context,
            Path targetPath,
            Path targetPhonePath,
            String sftpAddress,
            String sftpUsername,
            String sftpPassword)
            throws InvalidSftpCredentialsException, SftpException, InvalidTargetFilePathException {
        this.context = context;
        int colonIndex = sftpAddress.indexOf(":");
        this.sftpAddress = sftpAddress.substring(0, colonIndex);
        this.sftpPort = Integer.parseInt(sftpAddress.substring(colonIndex + 1));
        this.sftpUsername = sftpUsername;
        this.sftpPassword = sftpPassword;
        connectToSftp();

        if (!exists(targetPath.getParent().toString())) {
            throw new InvalidTargetFilePathException(context, targetPath);
        }

        this.targetPath = targetPath;
        this.storageDirectoryPath = getStorageDirectoryPath(targetPath, targetPhonePath);
    }

    private Path getStorageDirectoryPath(Path targetDirectoryPath, Path targetPhonePath) {
        List<String> sftpDirs = Arrays.asList(targetDirectoryPath.toString().split("/"));
        List<String> phoneDirs = Arrays.asList(targetPhonePath.toString().split("/"));

        Collections.reverse(sftpDirs);
        Collections.reverse(phoneDirs);

        int minLength = Math.min(sftpDirs.size(), phoneDirs.size());
        int divergeIndex = 0;

        for (int i = 0; i < minLength; i++) {
            if (!sftpDirs.get(i).equals(phoneDirs.get(i))) {
                divergeIndex = i;
                break;
            }
        }

        List<String> uniqueSftpDirs = sftpDirs.subList(divergeIndex, sftpDirs.size());

        Collections.reverse(uniqueSftpDirs);

        return Paths.get(String.join("/", uniqueSftpDirs));
    }

    @Override
    public Path getTargetPath() {
        return targetPath;
    }

    @Override
    public Path getStorageDirectoryPath() {
        return storageDirectoryPath;
    }

    @Override
    public List<String> getLoggedStructure(Path path, boolean withTags) throws SftpException {
        notifyOperationExecuted(context.getString(R.string.reading_sftp_storage));
        List<String> loggedStructure = new ArrayList<>();
        if (!channelSftp.lstat(path.toString()).isDir()) {
            notifyOperationExecuted(
                    "\n\n   " + context.getString(R.string.read_operation) + " " + path);
            if (withTags) {
                loggedStructure.add(SFTP_LOG_TAG + path.toString());
            } else {
                loggedStructure.add(path.toString());
            }
        } else {
            loggedStructure = recursiveLogStructure(path, new LinkedList<>(), withTags);
            loggedStructure.remove(0);
        }
        return loggedStructure;
    }

    private List<String> recursiveLogStructure(Path path, List<String> log, boolean withTags)
            throws SftpException {
        String absolutePath = path.toString();
        notifyOperationExecuted(
                "\n\n   " + context.getString(R.string.read_operation) + " " + absolutePath);
        if (withTags) {
            log.add(SFTP_LOG_TAG + absolutePath);
        } else {
            log.add(absolutePath);
        }

        Vector<ChannelSftp.LsEntry> entries = channelSftp.ls(absolutePath);
        for (ChannelSftp.LsEntry entry : entries) {
            String fileName = entry.getFilename();
            if (!fileName.equals(".") && !fileName.equals("..")) {
                String fullPath = path + "/" + fileName;
                if (entry.getAttrs().isDir()) {
                    recursiveLogStructure(Paths.get(fullPath), log, withTags);
                } else {
                    notifyOperationExecuted(
                            "\n\n   "
                                    + context.getString(R.string.read_operation)
                                    + " "
                                    + fullPath);
                    if (withTags) {
                        log.add(SFTP_LOG_TAG + fullPath);
                    } else {
                        log.add(fullPath);
                    }
                }
            }
        }

        return log;
    }

    public List<String> copyFilesFromPhone(Map<String, String> paths, boolean skipIfExists)
            throws IOException, SftpException {
        if (!paths.isEmpty()) {
            notifyOperationExecuted(
                    context.getString(R.string.copying_files_dirs_from_phone_to_sftp));
        }

        List<String> createdFiles = new LinkedList<>();

        for (Map.Entry<String, String> entry : paths.entrySet()) {
            Path phonePath = Paths.get(entry.getKey());
            String sftpPath = entry.getValue();

            if (skipIfExists && exists(sftpPath)) {
                continue;
            }

            if (Files.isDirectory(phonePath)) {
                createDirectories(sftpPath);
            } else {
                copyFileFromPhone(phonePath, sftpPath);
            }

            createdFiles.add(entry.getValue());
        }

        return createdFiles;
    }

    public void copyFileFromPhone(Path phonePath, String sftpPath)
            throws IOException, SftpException {
        notifyOperationExecuted(
                "\n\n   " + context.getString(R.string.copy_operation) + " " + phonePath);
        createDirectories(Paths.get(sftpPath).getParent().toString());
        try (InputStream inputStream = Files.newInputStream(phonePath.toFile().toPath())) {
            uploadFile(inputStream, sftpPath);
            Files.setLastModifiedTime(phonePath, FileTime.fromMillis(System.currentTimeMillis()));
        }
    }

    @Override
    public void deleteFiles(List<String> pathsForDeleting) throws SftpException {
        if (!pathsForDeleting.isEmpty()) {
            notifyOperationExecuted(context.getString(R.string.deleting_files_dirs_on_sftp));
        }

        for (String sftpPath : pathsForDeleting) {
            deleteRecursively(sftpPath);
        }
    }

    private void deleteRecursively(String sftpPath) throws SftpException {
        try {
            SftpATTRS attrs = channelSftp.lstat(sftpPath);
            if (attrs.isDir()) {
                Vector<ChannelSftp.LsEntry> entries = channelSftp.ls(sftpPath);
                for (ChannelSftp.LsEntry entry : entries) {
                    String entryName = entry.getFilename();
                    if (!entryName.equals(".") && !entryName.equals("..")) {
                        deleteRecursively(sftpPath + "/" + entryName);
                    }
                }
                notifyOperationExecuted(
                        "\n\n   " + context.getString(R.string.del_dir_operation) + " " + sftpPath);
                channelSftp.rmdir(sftpPath);
            } else {
                notifyOperationExecuted(
                        "\n\n   "
                                + context.getString(R.string.del_file_operation)
                                + " "
                                + sftpPath);
                channelSftp.rm(sftpPath);
            }
        } catch (SftpException e) {
            if (ChannelSftp.SSH_FX_NO_SUCH_FILE != e.id) {
                throw e;
            }
        }
    }

    public InputStream downloadFile(String path) throws SftpException {
        return channelSftp.get(path);
    }

    public void uploadFile(InputStream inputStream, String sftpPath) throws SftpException {
        notifyOperationExecuted(
                "\n\n   " + context.getString(R.string.upload_operation) + " " + sftpPath);
        channelSftp.put(inputStream, sftpPath);
    }

    public SftpATTRS getAttributes(String sftpPath) throws SftpException {
        return channelSftp.lstat(sftpPath);
    }

    public boolean exists(String sftpPath) throws SftpException {
        try {
            channelSftp.stat(sftpPath);
            return true;
        } catch (SftpException e) {
            if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                return false;
            } else {
                throw e;
            }
        }
    }

    public void createDirectories(String sftpPath) throws SftpException {
        String[] folders = sftpPath.split("/");
        String path = "";
        for (String folder : folders) {
            if (folder.isEmpty()) {
                continue;
            }
            path += "/" + folder;
            try {
                channelSftp.stat(path);
            } catch (SftpException e) {
                if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                    notifyOperationExecuted(
                            "\n\n   "
                                    + context.getString(R.string.create_dir_operation)
                                    + " "
                                    + path);
                    channelSftp.mkdir(path);
                } else {
                    throw e;
                }
            }
        }
    }

    public void updateModificationTime(String path) throws SftpException {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        int currentTimeInSeconds = (int) (calendar.getTimeInMillis() / 1000);
        channelSftp.setMtime(path, currentTimeInSeconds);
    }

    private void connectToSftp() throws InvalidSftpCredentialsException {
        try {
            JSch jsch = new JSch();
            session = jsch.getSession(sftpUsername, sftpAddress, sftpPort);
            session.setPassword(sftpPassword);

            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);

            session.connect();

            channelSftp = (ChannelSftp) session.openChannel("sftp");
            channelSftp.connect();
        } catch (JSchException e) {
            throw new InvalidSftpCredentialsException(context);
        }
    }

    public void closeConnection() {
        channelSftp.disconnect();
        session.disconnect();
        notifyOperationExecuted(context.getString(R.string.sftp_connection_close));
    }
}
