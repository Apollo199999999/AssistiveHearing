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

public class HowToDialog extends Dialog {

    public Activity c;
    public Dialog d;
    public Button yes;

    public HowToDialog(Activity a) {
        // Required empty public constructor
        super(a);
        this.c = a;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.how_to_dialog);

        Button understandBtn = (Button) findViewById(R.id.btn_understand);
        understandBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dismiss();
            }
        });
    }

}