package com.vhark.sftp_synchronizer.exception;

import android.content.Context;

import com.vhark.sftp_synchronizer.R;

public class InvalidSftpCredentialsException extends Exception {
    public InvalidSftpCredentialsException(Context context) {
        super(context.getString(R.string.invalid_sftp_credentials_exception));
    }
}
