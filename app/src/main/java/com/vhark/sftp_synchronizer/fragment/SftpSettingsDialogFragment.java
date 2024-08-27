package com.vhark.sftp_synchronizer.fragment;

import static com.vhark.sftp_synchronizer.constant.PrefsKeys.*;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.vhark.sftp_synchronizer.MainActivity;
import com.vhark.sftp_synchronizer.R;
import com.vhark.sftp_synchronizer.constant.LogsPath;
import com.vhark.sftp_synchronizer.constant.PrefsConstants;
import com.vhark.sftp_synchronizer.shared.SharedViewModel;

public class SftpSettingsDialogFragment extends DialogFragment {

    private EditText sftpAddressInput;
    private EditText usernameInput;
    private EditText passwordInput;
    private EditText defaultSftpPathInput;
    private CheckBox disableDeleteFunction;
    private Button saveSftpButton;
    private Button openLogFileSftpSettings;
    private Button openOperationsLogFileSftpSettings;
    private SharedViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        PrefsConstants.instance(this.getActivity());
        getDialog().getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        View view = inflater.inflate(R.layout.dialog_sftp_settings, container, false);

        initializeViews(view);
        loadAllSftpSettings();

        viewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);
        MainActivity mainActivity = (MainActivity) getActivity();

        openLogFileSftpSettings.setOnClickListener(v -> mainActivity.openLogFile(LogsPath.LOGS));

        openOperationsLogFileSftpSettings.setOnClickListener(
                v -> mainActivity.openLogFile(LogsPath.OPERATIONS));

        saveSftpButton.setOnClickListener(
                v -> {
                    if (validateInputs()) {
                        storeAllSftpSettings();
                        viewModel.requestUpdateButtons();
                        showToast(getString(R.string.saved));
                        dismiss();
                    }
                });

        return view;
    }

    private void initializeViews(View view) {
        sftpAddressInput = view.findViewById(R.id.sftpAddressInput);
        usernameInput = view.findViewById(R.id.usernameInput);
        passwordInput = view.findViewById(R.id.passwordInput);
        defaultSftpPathInput = view.findViewById(R.id.defaultSftpPathInput);
        disableDeleteFunction = view.findViewById(R.id.disableDeleteFunction);
        openLogFileSftpSettings = view.findViewById(R.id.openLogFileSftpSettings);
        openOperationsLogFileSftpSettings =
                view.findViewById(R.id.openOperationsLogFileSftpSettings);
        saveSftpButton = view.findViewById(R.id.saveSftpButton);
    }

    private boolean validateInputs() {
        String sftpAddress = sftpAddressInput.getText().toString().trim();
        String username = usernameInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();

        if (TextUtils.isEmpty(sftpAddress)) {
            showToast(getString(R.string.sftp_address_cannot_empty));
            return false;
        } else if (TextUtils.isEmpty(username)) {
            showToast(getString(R.string.username_cannot_empty));
            return false;
        } else if (TextUtils.isEmpty(password)) {
            showToast(getString(R.string.password_cannot_empty));
            return false;
        }

        return true;
    }

    private void storeAllSftpSettings() {
        String sftpAddress = sftpAddressInput.getText().toString().trim();
        String username = usernameInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();
        String defaultSftpPath = defaultSftpPathInput.getText().toString().trim();
        boolean disabledDeleting = disableDeleteFunction.isChecked();

        PrefsConstants.instance().storeValueString(KEY_SFTP_ADDRESS, sftpAddress);
        PrefsConstants.instance().storeValueString(KEY_USERNAME, username);
        PrefsConstants.instance().storeValueString(KEY_PASSWORD, password);
        PrefsConstants.instance().storeValueString(KEY_DEFAULT_SFTP_PATH, defaultSftpPath);
        PrefsConstants.instance().storeValueBoolean(KEY_DELETE_FUNCTION_DISABLED, disabledDeleting);
    }

    private void loadAllSftpSettings() {
        sftpAddressInput.setText(PrefsConstants.instance().fetchValueString(KEY_SFTP_ADDRESS));
        usernameInput.setText(PrefsConstants.instance().fetchValueString(KEY_USERNAME));
        passwordInput.setText(PrefsConstants.instance().fetchValueString(KEY_PASSWORD));
        defaultSftpPathInput.setText(
                PrefsConstants.instance().fetchValueString(KEY_DEFAULT_SFTP_PATH));
        disableDeleteFunction.setChecked(
                PrefsConstants.instance().fetchValueBoolean(KEY_DELETE_FUNCTION_DISABLED));
    }

    private void showToast(String message) {
        Toast.makeText(getActivity(), message, Toast.LENGTH_SHORT).show();
    }
}
