package com.vhark.sftp_synchronizer.exception;

import android.content.Context;

import com.vhark.sftp_synchronizer.R;

public class LogFileCreationException extends Exception {
    public LogFileCreationException(Context context, String logFileName) {
        super(context.getString(R.string.log_file_creation_exception, logFileName));
    }
}
