package com.example.android.bluetoothlegatt;

import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

public class ResultActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);
    }

    public void back2Control(View view) {
        Intent intent = new Intent(this, DeviceControlActivity.class);
        startActivity(intent);
    }
}