package com.sic.assistivehearing;

import android.app.Activity;
import android.app.Dialog;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;

public class SettingsDialog extends Dialog {

    public Activity c;
    public Dialog d;
    public Button yes;

    public SettingsDialog(Activity a) {
        // Required empty public constructor
        super(a);
        this.c = a;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.settings_dialog);

        // Set invertSwitch from SharedPreferences
        SwitchCompat invertSwitch = findViewById(R.id.invertSwitch);
        SharedPreferences settings = getContext().getSharedPreferences("UserSettings", 0);
        if (settings.getBoolean("inversedMicrophone", false) != false) {
            invertSwitch.setChecked(settings.getBoolean("inversedMicrophone", false));
        }

        yes = (Button) findViewById(R.id.btn_yes);
        yes.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Write the state of the invertSwitch to settings
                SwitchCompat invertSwitch = findViewById(R.id.invertSwitch);
                SharedPreferences settings = getContext().getSharedPreferences("UserSettings", 0);
                SharedPreferences.Editor editor = settings.edit();
                editor.putBoolean("inversedMicrophone", invertSwitch.isChecked());
                editor.commit();
                dismiss();
            }
        });
    }

}