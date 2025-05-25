package io.benwiegand.atvremote.phone.ui;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.color.DynamicColors;

import io.benwiegand.atvremote.phone.R;

public abstract class DynamicColorsCompatActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // applying themes using "@color/material_dynamic_*" will cause a crash if api level doesn't support it
        if (DynamicColors.isDynamicColorAvailable()) {
            setTheme(R.style.Theme_ATVRemote_DynamicColors);
        }
    }
}
