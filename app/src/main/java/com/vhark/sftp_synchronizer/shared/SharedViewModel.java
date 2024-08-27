package com.vhark.sftp_synchronizer.shared;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class SharedViewModel extends ViewModel {

    private final MutableLiveData<Boolean> cancelRequested = new MutableLiveData<>();
    private final MutableLiveData<Boolean> updateButtonRequested = new MutableLiveData<>();

    public void requestCancel() {
        cancelRequested.setValue(true);
    }

    public LiveData<Boolean> getCancelRequested() {
        return cancelRequested;
    }

    public void requestUpdateButtons() {
        updateButtonRequested.setValue(true);
    }

    public LiveData<Boolean> getUpdateButtonRequested() {
        return updateButtonRequested;
    }
}
