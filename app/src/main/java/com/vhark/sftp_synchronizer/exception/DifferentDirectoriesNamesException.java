package com.vhark.sftp_synchronizer.exception;


import android.content.Context;

import com.vhark.sftp_synchronizer.R;

public class DifferentDirectoriesNamesException extends Exception {
    public DifferentDirectoriesNamesException(Context context) {
        super(context.getString(R.string.different_directories_names_exception));
    }
}
