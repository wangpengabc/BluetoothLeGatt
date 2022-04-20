/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.bluetoothlegatt;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import androidx.annotation.RequiresApi;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ExpandableListView;
import android.widget.LinearLayout;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import com.example.android.bluetoothlegatt.predict.HRHRVStressArrhy;
import com.example.android.bluetoothlegatt.predict.QRSDetectorOffline;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lecho.lib.hellocharts.gesture.ZoomType;
import lecho.lib.hellocharts.model.Axis;
import lecho.lib.hellocharts.model.Line;
import lecho.lib.hellocharts.model.LineChartData;
import lecho.lib.hellocharts.model.PointValue;
import lecho.lib.hellocharts.view.LineChartView;

import org.apache.commons.codec.binary.Hex;

/**
 * For a given BLE device, this Activity provides the user interface to connect, display data,
 * and display GATT services and characteristics supported by the device.  The Activity
 * communicates with {@code BluetoothLeService}, which in turn interacts with the
 * Bluetooth LE API.
 */
public class DeviceControlActivity extends Activity {
    private final static String TAG = DeviceControlActivity.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    private TextView mConnectionState;
    private TextView mDataField;
    private LinearLayout llDataValue;
    private LinearLayout llConnectionState;
    private String mDeviceName;
    private String mDeviceAddress;
    private ExpandableListView mGattServicesList;
    private BluetoothLeService mBluetoothLeService;
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics =
            new ArrayList<ArrayList<BluetoothGattCharacteristic>>();
    private boolean mConnected = false;
    private BluetoothGattCharacteristic mNotifyCharacteristic;

    private final String LIST_NAME = "NAME";
    private final String LIST_UUID = "UUID";

    // 信号绘制部分变量
    private static final int DATA_MSG = 0x01;
    private LineChartView ppgChart = null;
    private Line ppgLine = null;
    private int refreshSize = 200;
    private Queue<Integer> drawDataQueue = null;

    // button 类控制
    private Button btnCollect;
    private Button btnRecord;
    private Button btnInference;
    private Button btnReturnToCollection;

    // received data
    private List<String> recordData = null;
    private boolean isRecord = false;
    private String externalFilesDirPath;
    private String dataSaveName;
    // used for calcaulting sampling freqency
    private long startTime = System.currentTimeMillis(); //起始时间
    private long endTime = System.currentTimeMillis(); //结束时间
    private long runTime = 0;

    // HRV指标
    private LinearLayout llCollecting;
    private LinearLayout llHRV;
    private TextView hrvPNN50;
    private TextView hrvRMSSD;
    private TextView hrvSDNN;
    private TextView hrvMEAN;
    private TextView hrvSD1;
    private TextView hrvSD2;
    private TextView hrvSDSD;
    private TextView hrvSD1SD2;
    private TextView hrvLF;
    private TextView hrvHF;
    private TextView hrvBAEVSKY;
    private TextView hrvNN50;
    private TextView hrvStress;
    private TextView hrvArrhy;

    // 健康指标
    private LinearLayout llHealthSuggestion;
    private TextView hrvResultHR;
    private TextView hrvProxyValue;
    private TextView hrvValueJudge;
    private TextView hrvResultStress;
    private TextView hrvResultArrhy;

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private int new_point_cnt = 0;
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @RequiresApi(api = Build.VERSION_CODES.O)
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                updateConnectionState(R.string.connected);
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                updateConnectionState(R.string.disconnected);
                invalidateOptionsMenu();
                clearUI();
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
                displayGattServices(mBluetoothLeService.getSupportedGattServices());
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
//                displayData(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));

                byte[] data_tmp = intent.getByteArrayExtra(BluetoothLeService.EXTRA_DATA);
                Message msg = new Message();
                msg.what = DATA_MSG;
                msg.obj = data_tmp;
                mHandler.sendMessage(msg);
            }
        }
    };


    // If a given GATT characteristic is selected, check for supported features.  This sample
    // demonstrates 'Read' and 'Notify' features.  See
    // http://d.android.com/reference/android/bluetooth/BluetoothGatt.html for the complete
    // list of supported characteristic features.

    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }

    private int ppg_started = 0;
    private final byte[] PPG_BEGIN_CMD = hexStringToByteArray("AA5501100415");
    private final byte[] PPG_STOP_CMD = hexStringToByteArray("AA550211010418");

    @SuppressLint("NonConstantResourceId")
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_collect:
//                if (btnCollect.getText().toString().equals("开始采集")) {
//                    btnCollect.setText("停止采集");
//                    isRecord = true;
//                }else{
//                    btnCollect.setText("开始采集");
//                    isRecord = false;
//                }
//                break;
        }
    }


    private final ExpandableListView.OnChildClickListener servicesListClickListner =
            new ExpandableListView.OnChildClickListener() {
                @Override
                public boolean onChildClick(ExpandableListView parent, View v, int groupPosition,
                                            int childPosition, long id) {
                    if (mGattCharacteristics != null) {


                        final BluetoothGattCharacteristic characteristic =
                                mGattCharacteristics.get(groupPosition).get(childPosition);
                        final int charaProp = characteristic.getProperties();

//                        Toast.makeText(DeviceControlActivity.this, "charaProp: ".concat(Integer.toHexString(charaProp)), Toast.LENGTH_LONG).show();
//
                        final String uuid = characteristic.getUuid().toString();
//                        Toast.makeText(DeviceControlActivity.this, uuid, Toast.LENGTH_LONG).show();
                        // 向 PPG 控制 characteristics 写指令
                        if(charaProp == BluetoothGattCharacteristic.PROPERTY_WRITE && uuid.equals("6e400002-b5a3-f393-e0a9-e50e24dcca9e")) {
                            if (ppg_started == 1) {
                                ppg_started = 0;
                                characteristic.setValue(PPG_STOP_CMD);
                                mBluetoothLeService.writeCharacteristic(characteristic);
                            } else {
                                ppg_started = 1;
                                characteristic.setValue(PPG_BEGIN_CMD);
                                mBluetoothLeService.writeCharacteristic(characteristic);
                                recordData = new ArrayList<>();
                            }
                        }

                        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
                            // If there is an active notification on a characteristic, clear
                            // it first so it doesn't update the data field on the user interface.
                            if (mNotifyCharacteristic != null) {
                                mBluetoothLeService.setCharacteristicNotification(
                                        mNotifyCharacteristic, false);
                                mNotifyCharacteristic = null;
                            }
                            mBluetoothLeService.readCharacteristic(characteristic);
                        }
                        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                            mNotifyCharacteristic = characteristic;
                            mBluetoothLeService.setCharacteristicNotification(
                                    characteristic, true);
                        }
                        return true;
                    }
                    return false;
                }
    };

    private void clearUI() {
        mGattServicesList.setAdapter((SimpleExpandableListAdapter) null);
        mDataField.setText(R.string.no_data);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.gatt_services_characteristics);

        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        // Sets up UI references.
        ((TextView) findViewById(R.id.device_address)).setText(mDeviceAddress);
        mGattServicesList = (ExpandableListView) findViewById(R.id.gatt_services_list);
        mGattServicesList.setOnChildClickListener(servicesListClickListner);
        mConnectionState = (TextView) findViewById(R.id.connection_state);
        mDataField = (TextView) findViewById(R.id.data_value);

        llDataValue = findViewById(R.id.ll_data_value);
        llConnectionState = findViewById(R.id.ll_connection_state);
        llDataValue.setVisibility(View.GONE);
        llConnectionState.setVisibility(View.GONE);

        getActionBar().setTitle(mDeviceName);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        // find corresponding view
        btnCollect = findViewById(R.id.btn_collect);
        btnRecord = findViewById(R.id.btn_record);
        btnInference = findViewById(R.id.btn_inference);
        btnReturnToCollection = findViewById(R.id.btn_return_to_collection);
        btnReturnToCollection.setVisibility(View.GONE);

        // HRV指标
        llCollecting = findViewById(R.id.ll_collecting);
        llHRV = findViewById(R.id.ll_hrv);
        hrvPNN50 = findViewById(R.id.hrv_pnn50);
        hrvRMSSD = findViewById(R.id.hrv_rmssd);
        hrvSDNN = findViewById(R.id.hrv_sdnn);
        hrvMEAN = findViewById(R.id.hrv_mean);
        hrvSD1 = findViewById(R.id.hrv_sd1);
        hrvSD2 = findViewById(R.id.hrv_sd2);
        hrvSDSD = findViewById(R.id.hrv_sdsd);
        hrvSD1SD2 = findViewById(R.id.hrv_sd1sd2);
        hrvLF = findViewById(R.id.hrv_lf);
        hrvHF = findViewById(R.id.hrv_hf);
        hrvBAEVSKY = findViewById(R.id.hrv_baevsky);
        hrvNN50 = findViewById(R.id.hrv_nn50);
        hrvStress = findViewById(R.id.hrv_stress);
        hrvArrhy = findViewById(R.id.hrv_arrhy);
        // 健康建议
        llHealthSuggestion = findViewById(R.id.ll_health_suggestion);
        hrvResultHR = findViewById(R.id.hrv_result_hr);
        hrvProxyValue = findViewById(R.id.hrv_proxy_value);
        hrvValueJudge = findViewById(R.id.hrv_value_judge);
        hrvResultStress = findViewById(R.id.hrv_result_stress);
        hrvResultArrhy = findViewById(R.id.hrv_result_arrhy);

                // plot
        ppgChart = findViewById(R.id.chart_ppg);

        drawDataQueue = new LinkedList<>();
        for(int i=0; i<refreshSize; i++) drawDataQueue.add(0);

        // data path for saving
        externalFilesDirPath = this.getExternalFilesDir(null).getAbsolutePath();
//        externalFilesDirPath = "/storage/emulated/0/新建文件夹";
    }

    // btn control
    public void collectControl(View view) {
        if (btnCollect.getText().toString().equals("开始采集")) {
            btnCollect.setText("停止采集");
            recordData = new ArrayList<>();
            isRecord = true;
            startTime = System.currentTimeMillis(); //起始时间

            // UI 控制
            llCollecting.setVisibility(View.VISIBLE);
            llHRV.setVisibility(View.GONE);
            llHealthSuggestion.setVisibility(View.GONE);
            btnRecord.setVisibility(View.VISIBLE);
            btnCollect.setVisibility(View.VISIBLE);
            btnInference.setVisibility(View.VISIBLE);
            btnReturnToCollection.setVisibility(View.GONE);

        }else{
            // 计算采样率
            endTime = System.currentTimeMillis(); //结束时间
            runTime = endTime - startTime;
            int total_len = 0;
            for (String s: recordData) {
                Log.d("s:", s);
                int s_len = s.length() / 2;
                total_len += s_len;
            }
            if (total_len != 0 && runTime != 0) {
                float fs = (float) runTime / (float) total_len;
                fs = 1000 / fs;
                Log.d("fs", String.valueOf(fs));
                Log.d("runTime", String.valueOf(runTime));
                Log.d("total_len", String.valueOf(total_len));
                Toast.makeText(DeviceControlActivity.this, String.valueOf(fs), Toast.LENGTH_SHORT).show();
            }

            // 相关UI和控制变量
            btnCollect.setText("开始采集");
            isRecord = false;
            // UI 控制
            llCollecting.setVisibility(View.VISIBLE);
            llHRV.setVisibility(View.GONE);
            llHealthSuggestion.setVisibility(View.GONE);
            btnCollect.setVisibility(View.VISIBLE);
            btnRecord.setVisibility(View.VISIBLE);
            btnInference.setVisibility(View.VISIBLE);
            btnReturnToCollection.setVisibility(View.GONE);
        }
    }

    public void returnToCollectionControl(View view) {
        btnRecord.setVisibility(View.VISIBLE);
        btnInference.setVisibility(View.VISIBLE);
        btnCollect.setVisibility(View.VISIBLE);
        btnReturnToCollection.setVisibility(View.GONE);

        llHealthSuggestion.setVisibility(View.GONE);
    }

    public void recordControl(View view) {
        if(saveData(recordData)){
            Toast.makeText(DeviceControlActivity.this,"保存成功！", Toast.LENGTH_SHORT).show();
//                        llChartView.setVisibility(View.INVISIBLE);
            // 清除用户信息
//            recordData.clear();
            drawDataQueue.clear();
        }
        else { Toast.makeText(DeviceControlActivity.this,"保存失败！", Toast.LENGTH_SHORT).show(); }
    }

    public void inferenceControl(View view) {
//        int data_point = 0;
//        String data_point_str;
//        List<Integer> points = new ArrayList<Integer>();
//        for (String s: recordData){
//            int s_len = s.length() / 2;
//            for (int i=0; i<s_len; i++) {
//                data_point = Integer.parseInt(s.substring(i*2, (i+1)*2), 16);
//                points.add(data_point);
//            }
//        }
        // detect peak of PPG and calculate HRV
        try {
            int lines = QRSDetectorOffline.detectQRS(externalFilesDirPath, dataSaveName);
            if (lines < 10) {
                Toast.makeText(this, "采集时间过短", Toast.LENGTH_SHORT);
            } else {
                // UI 控制
                llCollecting.setVisibility(View.GONE);
                llHRV.setVisibility(View.GONE);
                llHealthSuggestion.setVisibility(View.VISIBLE);
                btnRecord.setVisibility(View.GONE);
                btnInference.setVisibility(View.GONE);
                btnCollect.setVisibility(View.GONE);
                btnReturnToCollection.setVisibility(View.VISIBLE);
                String parameters_save_name = externalFilesDirPath + "/" + dataSaveName + "_paramters.properties";
                try {
                    Map<String, Double> parameters_map = HRHRVStressArrhy.loadFromFile(parameters_save_name);
                    hrvPNN50.setText("PNN50：" + String.format("%.2f", parameters_map.get("PNN50")));
                    hrvRMSSD.setText("RMSSD：" + String.format("%.2f", parameters_map.get("RMSSD")));
                    hrvSDNN.setText("SDNN：" + String.format("%.2f", parameters_map.get("SDNN")));
                    hrvMEAN.setText("MEAN：" + String.format("%.2f", parameters_map.get("MEAN")));
                    hrvSD1.setText("SD1：" + String.format("%.2f", parameters_map.get("SD1")));
                    hrvSD2.setText("SD2：" + String.format("%.2f", parameters_map.get("SD2")));
                    hrvSDSD.setText("SDSD：" + String.format("%.2f", parameters_map.get("SDSD")));
                    hrvSD1SD2.setText("SD1SD2：" + String.format("%.2f", parameters_map.get("SD1SD2")));
                    hrvLF.setText("LF：" + String.format("%.2f", parameters_map.get("LF")));
                    hrvHF.setText("HF：" + String.format("%.2f", parameters_map.get("HF")));
                    hrvBAEVSKY.setText("BAEVSKY：" + String.format("%.2f", parameters_map.get("BAEVSKY")));
                    hrvNN50.setText("NN50：" + String.format("%.2f", parameters_map.get("NN50")));
                    hrvStress.setText("Stress：" + String.format("%.2f", parameters_map.get("Stress")));
                    hrvArrhy.setText("Arrhy：" + String.format("%.2f", parameters_map.get("arrhy")));

                    // 健康建议
                    hrvResultHR.setText(String.format("%d", (int)(parameters_map.get("MEAN") * 1)));

                    hrvProxyValue.setText(String.format("%d", (int)(parameters_map.get("SDNN") * 1000)));
                    if ((int)(parameters_map.get("SDNN") * 1000) < 55) {
                        hrvValueJudge.setText("HRV值偏低，请注意休息");
                    } else {
                        hrvValueJudge.setText("HRV值正常");
                    }

                    if (parameters_map.get("Stress") < 2) {
                        hrvResultStress.setText("正常");
                    } else if (parameters_map.get("Stress") == 2) {
                        hrvResultStress.setText("偏高");
                    } else if (parameters_map.get("Stress") > 2) {
                        hrvResultStress.setText("高");
                    }

                    if (parameters_map.get("arrhy") == 0) {
                        hrvResultArrhy.setText("无异常");
                    } else if (parameters_map.get("arrhy") == 1) {
                        hrvResultArrhy.setText("心动过缓");
                    } else if (parameters_map.get("arrhy") == 2) {
                        hrvResultArrhy.setText("心动过速");
                    }

                }catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } finally {
            Toast.makeText(this, "采集时间过短", Toast.LENGTH_SHORT);
        }

//        // 切换到结果显示界面
//        Intent intent = new Intent(this, ResultActivity.class);
//        startActivity(intent);

        recordData.clear();
    }

    public boolean saveData(List<String> data) {
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) { //sd卡处于挂载状态
            String fileName_time = new SimpleDateFormat("yyyyMMddHHmm").format(new Date());
            dataSaveName = "testID" + "_" + fileName_time;
//            dataSaveName = id + "_" + userInfo.get(0);
            String fileName = dataSaveName + ".txt";
            Log.d("externalFilesDirPath", externalFilesDirPath);
            //获取要写入的文件目录  storage/sdcard/Android/data/包名/files/xxx.txt
            //创建指定目录下的文件
            File file = new File(externalFilesDirPath,fileName);
            //开始写文件
            if (!file.exists()) {
                try {
                    file.createNewFile();
                } catch (IOException e) {
                    e.printStackTrace();
                    return false;
                }
            }
            // 写入文件
            try {
                FileWriter fileWriter=new FileWriter(file);
//                for (String s: data){
//                    fileWriter.write(s);
//                }
                int data_point = 0;
                String data_point_str;
//                fileWriter.write("ppg,empty\n");
                for (String s: data){
                    Log.d("s:", s);
                    int s_len = s.length() / 2;
                    for (int i=0; i<s_len; i++) {
                        Log.d("s:", s.substring(i*2, (i+1)*2));
                        data_point = Integer.parseInt(s.substring(i*2, (i+1)*2), 16);
                        data_point_str = String.valueOf(data_point);
                        fileWriter.write(data_point_str);
                        fileWriter.write(","+data_point_str+"\n");
                    }
                }
                fileWriter.close();
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
            return true;
        } else {
            Toast.makeText(this, "找不到指定的SD卡", Toast.LENGTH_SHORT).show();
            return false;
        }
    }


    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.gatt_services, menu);
        if (mConnected) {
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(true);
        } else {
            menu.findItem(R.id.menu_connect).setVisible(true);
            menu.findItem(R.id.menu_disconnect).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_connect:
                mBluetoothLeService.connect(mDeviceAddress);
                return true;
            case R.id.menu_disconnect:
                mBluetoothLeService.disconnect();
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mConnectionState.setText(resourceId);
            }
        });
    }

    private void displayData(String data) {
        if (data != null) {
            mDataField.setText(data);
        }
    }

    // Demonstrates how to iterate through the supported GATT Services/Characteristics.
    // In this sample, we populate the data structure that is bound to the ExpandableListView
    // on the UI.
    private void displayGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null) return;
        String uuid = null;
        String unknownServiceString = getResources().getString(R.string.unknown_service);
        String unknownCharaString = getResources().getString(R.string.unknown_characteristic);
        ArrayList<HashMap<String, String>> gattServiceData = new ArrayList<HashMap<String, String>>();
        ArrayList<ArrayList<HashMap<String, String>>> gattCharacteristicData
                = new ArrayList<ArrayList<HashMap<String, String>>>();
        mGattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();

        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {
            HashMap<String, String> currentServiceData = new HashMap<String, String>();
            uuid = gattService.getUuid().toString();
            currentServiceData.put(
                    LIST_NAME, SampleGattAttributes.lookup(uuid, unknownServiceString));
            currentServiceData.put(LIST_UUID, uuid);
            gattServiceData.add(currentServiceData);

            ArrayList<HashMap<String, String>> gattCharacteristicGroupData =
                    new ArrayList<HashMap<String, String>>();
            List<BluetoothGattCharacteristic> gattCharacteristics =
                    gattService.getCharacteristics();
            ArrayList<BluetoothGattCharacteristic> charas =
                    new ArrayList<BluetoothGattCharacteristic>();

            // Loops through available Characteristics.
            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                charas.add(gattCharacteristic);
                HashMap<String, String> currentCharaData = new HashMap<String, String>();
                uuid = gattCharacteristic.getUuid().toString();
                currentCharaData.put(
                        LIST_NAME, SampleGattAttributes.lookup(uuid, unknownCharaString));
                currentCharaData.put(LIST_UUID, uuid);
                gattCharacteristicGroupData.add(currentCharaData);
            }
            mGattCharacteristics.add(charas);
            gattCharacteristicData.add(gattCharacteristicGroupData);
        }

        SimpleExpandableListAdapter gattServiceAdapter = new SimpleExpandableListAdapter(
                this,
                gattServiceData,
                android.R.layout.simple_expandable_list_item_2,
                new String[] {LIST_NAME, LIST_UUID},
                new int[] { android.R.id.text1, android.R.id.text2 },
                gattCharacteristicData,
                android.R.layout.simple_expandable_list_item_2,
                new String[] {LIST_NAME, LIST_UUID},
                new int[] { android.R.id.text1, android.R.id.text2 }
        );
        mGattServicesList.setAdapter(gattServiceAdapter);
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    // 图表相关函数——根据数据生成线条
    private void generateLine(Queue<Integer> queue) {
        List<PointValue> ppgPoints = new ArrayList<>();

        // 分割原始数据
        int idx = 0;
        for (int data_point : queue) {
            int data0 = data_point;
            ppgPoints.add(new PointValue(idx, data0));
            idx++;
        }

        // 根据创建的 点 创建 线条， 并设置线条的外观
        ppgLine = new Line(ppgPoints);
        ppgLine.setColor(Color.parseColor("#D54C4C"));
        ppgLine.setStrokeWidth(2);
        ppgLine.setCubic(true);  //设置是平滑的还是直的
        ppgLine.setPointRadius(2);
        ppgLine.setHasLabelsOnlyForSelected(true);
    }

    // 图表相关函数 - 绘图
    private void drawChart(LineChartView chart, String chartName) {
        List<Line> lines = new ArrayList<>();
        if (chartName.equals("PPG")) lines.add(ppgLine);

        chart.setInteractive(true);
        chart.setZoomType(ZoomType.HORIZONTAL_AND_VERTICAL);
        LineChartData data = new LineChartData();
        Axis axisX = new Axis();//x轴
        Axis axisY = new Axis();//y轴;
//        // 设置信号的纵轴数据
//        axisY.setMaxLabelChars(6);//max label length, for example 60
//        List<AxisValue> values = new ArrayList<>();
//        for(int i = 0; i < 100; i+= 10){
//            AxisValue value = new AxisValue(i);
//            String label = "";
//            value.setLabel(label);
//            values.add(value);
//        }
//        axisY.setValues(values);
        axisX.setName(chartName);
        axisX.setTextSize(10);
        axisY.setTextSize(7);
        axisX.setTextColor(Color.parseColor("#323232"));
        axisY.setTextColor(Color.parseColor("#323232"));
        axisY.setInside(true);	//将轴数据显示在内侧
        axisX.setHasLines(true);//设置是否显示坐标网格。
        axisY.setHasLines(true);//设置是否显示坐标网格。
        axisX.setLineColor(Color.parseColor("#66F7B77D"));//设置网格线的颜色
        axisY.setLineColor(Color.parseColor("#66F7B77D"));//设置网格线的颜色

        data.setAxisXBottom(axisX);
        data.setAxisYLeft(axisY);

        data.setLines(lines);
        chart.setLineChartData(data);  //给图表设置数据
        chart.setBackgroundColor(Color.parseColor("#FFFAF0"));
    }

    // Animators may only be run on Looper threads
    // 所以要这么传
    private String data_string = "0";
    private String total_string = "";

    @SuppressLint("HandlerLeak")
    private Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case DATA_MSG: //数据消息
                    if(isRecord) {
                        byte[] data = (byte[]) msg.obj;
                        // 第三种方法
                        if (data != null && data.length > 0) {
                            final StringBuilder stringBuilder = new StringBuilder(data.length);
                            String raw_data_string = BluetoothLeService.bytes2String(data);
                            Log.i("data: ", raw_data_string);
                            total_string += raw_data_string;
                        }

                        if (total_string.length() > 100) {
                            if (parseData() == 1) {
                                try {
                                    Collections.addAll(recordData, data_string);
                                    byte[] data_tmp = Hex.decodeHex(data_string.toCharArray());
                                    Log.i("data_bytes: ", BluetoothLeService.bytes2String(data_tmp));

                                    int data_point = 0;
                                    if (data_tmp != null) {
                                        new_point_cnt += data_tmp.length;
                                        for (byte tmp : data_tmp) {
                                            data_point = Byte.toUnsignedInt(tmp);
                                            if (drawDataQueue.size() > refreshSize) {
                                                drawDataQueue.poll();
                                            }
                                            drawDataQueue.add(data_point);
                                        }
                                    }

                                    // 调用画图函数
                                    // 产生线条 - 绘图
                                    if (drawDataQueue != null) {
                                        Log.i("Queue is not empty", "Queue is not empty");
                                        generateLine(drawDataQueue);
                                        drawChart(ppgChart, "PPG");
                                    }
                                } catch (Exception e) {
                                    byte[] data_bytes = new byte[]{0x00};
                                    Log.e("decode error", "decode");
                                }
                            }
                        }
                    }
                break;
            }
        }
    };

    public int parseData(){
        // 正则匹配
        int end = -1;
        int start = -1;
        int data_len = 0;
        String regex = "AA55\\w{2}A8\\w+";
        //创建一个模式对象
        Pattern pattern = Pattern.compile(regex);
        //匹配字符串
        Matcher matcher = pattern.matcher(total_string);
        // 初始化全局变量
        data_string = "";

        while (matcher.find()) {
            start = matcher.start();
            end = matcher.end();
            String data = matcher.group();
            data_len = Integer.decode("0x"+data.substring(4,6));
            if (start+data_len*2+10 > total_string.length() && data_string.equals("")) {
                total_string = total_string.substring(start, total_string.length());
                return -1;
            } else if(start+data_len*2+10 > total_string.length() && data_string.length() > 2) {
                total_string = total_string.substring(start, total_string.length());
                return 1;
            }

            data_string += data.substring(8,8+data_len*2);
            Log.i("FOUND: ", data_string);
            Log.i("data Len:", String.valueOf(data_len));

            total_string = total_string.substring(start+data_len*2+10, total_string.length());
            matcher = pattern.matcher(total_string);
        }

        if (data_string.equals("")) {
            total_string = "";
            return -1;
        } else {
            return 1;
        }

    }


}

