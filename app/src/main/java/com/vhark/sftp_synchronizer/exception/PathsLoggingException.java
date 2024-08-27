package com.vhark.sftp_synchronizer.exception;

import android.content.Context;

import com.vhark.sftp_synchronizer.R;

public class PathsLoggingException extends Exception {
    public PathsLoggingException(Context context) {
        super(context.getString(R.string.paths_logging_exception));
    }
}
