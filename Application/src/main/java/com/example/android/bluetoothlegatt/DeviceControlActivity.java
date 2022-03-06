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
import android.os.IBinder;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ExpandableListView;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;

import lecho.lib.hellocharts.gesture.ZoomType;
import lecho.lib.hellocharts.model.Axis;
import lecho.lib.hellocharts.model.Line;
import lecho.lib.hellocharts.model.LineChartData;
import lecho.lib.hellocharts.model.PointValue;
import lecho.lib.hellocharts.view.LineChartView;

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
    private LineChartView ppgChart = null;
    private Line ppgLine = null;
    private int refreshSize = 200;
    private Queue<Integer> drawDataQueue = null;

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
                int data_point;
                int fsm_stage = 0;
                int data_len = 0;
                int data_cnt = 0;
                if (data_tmp != null) {
//                    StringBuilder stringBuilder = new StringBuilder(data_tmp.length);
//                    for(byte byteChar : data_tmp)
//                        stringBuilder.append(String.format("%02X ", byteChar));
//                    Log.i( "received data: ", stringBuilder.toString());

                    for (byte tmp : data_tmp) {
//                        Log.i("fsm", "fsm_stage:".concat(String.valueOf(fsm_stage)));
                        Log.i("fsm", "fsm_stage:".concat(String.valueOf(fsm_stage)));
                        Log.i("received data:", "received data: ".concat(String.format("%02X ", tmp)));
                        switch (fsm_stage) {
                            case 0:
                                if (Byte.compare(tmp, (byte)0xAA) ==  0) {
                                    fsm_stage = 1;
                                }
                                break;

                            case 1:
                                if (Byte.compare(tmp, (byte)0x55) ==  0) {
                                    fsm_stage = 2;
                                } else {
                                    fsm_stage = 0;
                                }
                                break;

                            case 2:
                            data_len = Byte.toUnsignedInt(tmp);
//                                data_len = tmp;
                                fsm_stage = 3;
                                break;

                            case 3:
                                if (Byte.compare(tmp, (byte)0xA8) == 0) {
                                    fsm_stage = 4;
                                } else {
                                    fsm_stage = 0;
                                }
                                break;

                            case 4:
                                if (data_cnt < data_len) {
                                    data_cnt++;
                                    if (data_cnt == data_len) {
                                        fsm_stage = 0;
                                    }
                                data_point = Byte.toUnsignedInt(tmp);
//                                    data_point = tmp;
                                    if (drawDataQueue.size() > refreshSize) {
                                        drawDataQueue.poll();
                                    }
                                    drawDataQueue.add(data_point);
                                }
                                break;

                            default:
                                break;
                        }
                    }
                }
                // 调用画图函数
                // 产生线条 - 绘图
                if (drawDataQueue != null) {
                    Log.i("Queue is not empty", "Queue is not empty");
                    generateLine(drawDataQueue);
                    drawChart(ppgChart, "PPG");
                }
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

        getActionBar().setTitle(mDeviceName);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        // plot
        ppgChart = findViewById(R.id.chart_ppg);

        drawDataQueue = new LinkedList<>();
        for(int i=0; i<refreshSize; i++) drawDataQueue.add(0);
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
//        for (String str: queue) {
//            String[] split_data = str.split(",");
//            int data0 = Integer.parseInt(split_data[0]);
//            int data1 = Integer.parseInt(split_data[1]);
//            ppgPoints.add(new PointValue(idx, data0));
//            idx++;
//        }

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
}

