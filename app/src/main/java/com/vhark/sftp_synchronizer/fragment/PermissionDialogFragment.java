package com.vhark.sftp_synchronizer.fragment;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.vhark.sftp_synchronizer.MainActivity;
import com.vhark.sftp_synchronizer.R;
import com.vhark.sftp_synchronizer.constant.PrefsConstants;

public class PermissionDialogFragment extends DialogFragment {

    private Button gruntPermissionButton;
    private Button closeAppButton;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        PrefsConstants.instance(this.getActivity());
        getDialog().getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        getDialog().setCancelable(false);
        View view = inflater.inflate(R.layout.dialog_request_permission, container, false);
        initializeViews(view);
        MainActivity mainActivity = (MainActivity) getActivity();

        gruntPermissionButton.setOnClickListener(
                v -> {
                    Intent intent = new Intent();
                    intent.setAction(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    Uri uri = Uri.fromParts("package", mainActivity.getPackageName(), null);
                    intent.setData(uri);
                    startActivity(intent);
                });

        closeAppButton.setOnClickListener(
                v -> {
                    mainActivity.finishAffinity();
                });

        return view;
    }

    private void initializeViews(View view) {
        gruntPermissionButton = view.findViewById(R.id.gruntPermissionButton);
        closeAppButton = view.findViewById(R.id.closeAppButton);
    }

    @Override
    public void onResume() {
        super.onResume();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                dismiss();
            }
        } else {
            dismiss();
        }

        if (getDialog() != null) {
            WindowManager.LayoutParams params = getDialog().getWindow().getAttributes();
            params.width = ViewGroup.LayoutParams.MATCH_PARENT;
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            getDialog().getWindow().setAttributes(params);
        }
    }
}
