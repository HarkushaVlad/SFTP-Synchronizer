package com.vhark.sftp_synchronizer.constant;

public enum LogTags {
    PHONE_LOG_TAG("P::"),
    SFTP_LOG_TAG("S::"),
    MERGE_PATH_LOG_TAG("MERGE::");

    private String tag;

    LogTags(String tag) {
        this.tag = tag;
    }

    @Override
    public String toString() {
        return tag;
    }
}
