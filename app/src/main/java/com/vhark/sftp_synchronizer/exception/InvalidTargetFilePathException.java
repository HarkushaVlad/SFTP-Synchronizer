package com.vhark.sftp_synchronizer.exception;

import android.content.Context;

import com.vhark.sftp_synchronizer.R;

import java.nio.file.Path;

public class InvalidTargetFilePathException extends Exception {
    public InvalidTargetFilePathException(Context context, Path path) {
        super(context.getString(R.string.invalid_target_file_path_exception, path));
    }
}
