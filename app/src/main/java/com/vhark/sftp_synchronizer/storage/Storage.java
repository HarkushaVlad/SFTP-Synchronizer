package com.vhark.sftp_synchronizer.storage;

import android.content.Context;

import com.jcraft.jsch.SftpException;
import com.vhark.sftp_synchronizer.constant.LogsPath;
import com.vhark.sftp_synchronizer.shared.OperationExecutionListener;
import com.vhark.sftp_synchronizer.R;
import com.vhark.sftp_synchronizer.exception.LogFileCreationException;
import com.vhark.sftp_synchronizer.exception.PathsLoggingException;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public abstract class Storage {

    private OperationExecutionListener operationExecutionListener;

    public void setOperationExecutionListener(OperationExecutionListener listener) {
        this.operationExecutionListener = listener;
    }

    protected void notifyOperationExecuted(String information) {
        if (operationExecutionListener != null) {
            operationExecutionListener.onOperationExecute(information);
        }
    }

    public void writeLogs(Context context) throws PathsLoggingException, LogFileCreationException {
        try {
            File logFile = StorageUtils.getLogFile(context, LogsPath.LOGS);
            List<String> logsList = getLoggedStructure(getTargetPath(), true);

            try (BufferedWriter bufferedWriter =
                    new BufferedWriter(new FileWriter(logFile, true))) {
                notifyOperationExecuted(context.getString(R.string.writing_log));
                for (String line : logsList) {
                    notifyOperationExecuted(
                            "\n\n   " + context.getString(R.string.write_operation) + " " + line);
                    bufferedWriter.write(line);
                    bufferedWriter.newLine();
                }
            }
        } catch (IOException | SftpException e) {
            throw new PathsLoggingException(context);
        }
    }

    public abstract Path getTargetPath();

    public abstract Path getStorageDirectoryPath();

    public abstract List<String> getLoggedStructure(Path path, boolean withTags)
            throws SftpException;

    public abstract void deleteFiles(List<String> pathsForDeleting)
            throws SftpException, IOException;
}
