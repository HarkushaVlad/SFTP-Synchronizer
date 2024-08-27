package com.vhark.sftp_synchronizer.exception;

import android.content.Context;

import com.vhark.sftp_synchronizer.R;

public class LogsReadingException extends Exception {
    public LogsReadingException(Context context) {
        super(context.getString(R.string.logs_reading_exception));
    }
}
