package com.vhark.sftp_synchronizer.constant;

public enum LogsPath {
    LOGS("log.txt"),
    OPERATIONS("operations_log.txt");

    private String path;

    LogsPath(String path) {
        this.path = path;
    }

    @Override
    public String toString() {
        return path;
    }
}
