package com.vhark.sftp_synchronizer.constant;

public enum PrefsKeys {
    KEY_PHONE_PATH("phonePath"),
    KEY_SFTP_PATH("sftpPath"),
    KEY_SFTP_ADDRESS("sftpAddress"),
    KEY_USERNAME("username"),
    KEY_PASSWORD("password"),
    KEY_DEFAULT_SFTP_PATH("defaultSftpPath"),
    KEY_AUTO_CONFIRM_ENABLED("autoConfirmEnabled"),
    KEY_DELETE_FUNCTION_DISABLED("deleteFunctionDisabled");


    private String key;

    PrefsKeys(String key) {
        this.key = key;
    }

    @Override
    public String toString() {
        return key;
    }
}
