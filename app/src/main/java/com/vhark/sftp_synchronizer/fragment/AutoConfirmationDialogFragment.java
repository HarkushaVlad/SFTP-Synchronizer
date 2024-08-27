package com.vhark.sftp_synchronizer.fragment;

import static com.vhark.sftp_synchronizer.constant.PrefsKeys.KEY_AUTO_CONFIRM_ENABLED;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.vhark.sftp_synchronizer.MainActivity;
import com.vhark.sftp_synchronizer.R;
import com.vhark.sftp_synchronizer.constant.PrefsConstants;

public class AutoConfirmationDialogFragment extends DialogFragment {

    private Button cancelAutoConfirmButton;
    private Button confirmAutoConfirmButton;
    private CheckBox autoConfirmCheckBox;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        PrefsConstants.instance(this.getActivity());
        getDialog().getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        View view = inflater.inflate(R.layout.dialog_auto_confirmation, container, false);
        initializeViews(view);

        confirmAutoConfirmButton.setOnClickListener(
                v -> {
                    PrefsConstants.instance().storeValueBoolean(KEY_AUTO_CONFIRM_ENABLED, true);
                    autoConfirmCheckBox.setChecked(true);
                    showToast(getString(R.string.auto_confirmation_enabled));
                    dismiss();
                });

        cancelAutoConfirmButton.setOnClickListener(v -> dismiss());

        return view;
    }

    private void initializeViews(View view) {
        MainActivity mainActivity = (MainActivity) getActivity();
        if (mainActivity != null) {
            autoConfirmCheckBox = mainActivity.findViewById(R.id.autoConfirmCheckBox);
        }
        confirmAutoConfirmButton = view.findViewById(R.id.confirmAutoConfirmButton);
        cancelAutoConfirmButton = view.findViewById(R.id.cancelAutoConfirmButton);
    }

    @Override
    public void onResume() {
        super.onResume();

        if (getDialog() != null) {
            WindowManager.LayoutParams params = getDialog().getWindow().getAttributes();
            params.width = ViewGroup.LayoutParams.MATCH_PARENT;
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            getDialog().getWindow().setAttributes(params);
        }
    }

    private void showToast(String message) {
        Toast.makeText(getActivity(), message, Toast.LENGTH_SHORT).show();
    }
}
