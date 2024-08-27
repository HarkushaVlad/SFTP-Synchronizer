package com.vhark.sftp_synchronizer.exception;

import android.content.Context;

import com.vhark.sftp_synchronizer.R;

public class IncompatiblePathException extends Exception {
    public IncompatiblePathException(Context context) {
        super(context.getString(R.string.incompatible_path_exception));
    }
}
