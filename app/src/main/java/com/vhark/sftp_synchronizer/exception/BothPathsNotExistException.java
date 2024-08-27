package com.vhark.sftp_synchronizer.exception;


import android.content.Context;

import com.vhark.sftp_synchronizer.R;

public class BothPathsNotExistException extends Exception {
    public BothPathsNotExistException(Context context) {
        super(context.getString(R.string.both_paths_not_exist_exception));
    }
}
