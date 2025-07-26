package com.example.tooldriver;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.util.List;
import java.util.UUID;

public class BleManager {
    private static final String TAG = "BleManager";
    private static final String DEVICE_NAME = "30G 238.29";

    private final Context context;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCharacteristic writeCharacteristic;
    private BleConnectionListener connectionListener;
    public interface BleConnectionListener {
        void onConnected();
        void onDisconnected();
        void onConnectionFailed();
    }
    public void setConnectionListener(BleConnectionListener listener) {
        this.connectionListener = listener;
    }

    public BleManager(Context context) {
        this.context = context;

        BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager != null) {
            bluetoothAdapter = bluetoothManager.getAdapter();
            if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
                bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
            } else {
                Log.e(TAG, "Bluetooth is disabled");
            }
        } else {
            Log.e(TAG, "BluetoothManager is null");
        }
    }
    @SuppressLint("MissingPermission")
    public void startScan() {
        if (bluetoothLeScanner != null) {
            Log.d(TAG, "Start scanning...");
            bluetoothLeScanner.startScan(scanCallback);
        } else {
            Log.e(TAG, "bluetoothLeScanner is null");
        }
    }
    @SuppressLint("MissingPermission")
    public void stopScan() {
        if (bluetoothLeScanner != null) {
            bluetoothLeScanner.stopScan(scanCallback);
            Log.d(TAG, "Stop scanning...");
        }
    }
    @SuppressLint("MissingPermission")
    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            String name = device.getName();
            if (name != null && name.equals(DEVICE_NAME)) {
                Log.d(TAG, "Found target device: " + name + " - " + device.getAddress());
                stopScan();
                connectToDevice(device);
            }
        }
    };
    @SuppressLint("MissingPermission")
    private void connectToDevice(BluetoothDevice device) {
        bluetoothGatt = device.connectGatt(context, false, gattCallback);
        Log.d(TAG, "Connecting to " + device.getAddress());
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.d(TAG, "onConnectionStateChange: status=" + status + ", newState=" + newState);
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "Connected. Discovering services...");
                bluetoothGatt.discoverServices();
                if (connectionListener != null) connectionListener.onConnected();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG, "Disconnected from GATT server");
                if (connectionListener != null) connectionListener.onDisconnected();
            } else if (status != BluetoothGatt.GATT_SUCCESS) {
                if (connectionListener != null) connectionListener.onConnectionFailed();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            Log.d(TAG, "onServicesDiscovered, status = " + status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                List<BluetoothGattService> services = gatt.getServices();
                for (BluetoothGattService service : services) {
                    for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                        int props = characteristic.getProperties();
                        if ((props & BluetoothGattCharacteristic.PROPERTY_WRITE) > 0) {
                            writeCharacteristic = characteristic;
                            Log.d(TAG, "Found write characteristic: " + characteristic.getUuid());
                            break;
                        }
                    }
                    if (writeCharacteristic != null) break;
                }

                if (writeCharacteristic == null) {
                    Log.e(TAG, "No write characteristic found");
                }
            } else {
                Log.e(TAG, "Service discovery failed");
            }
        }
    };
    @SuppressLint("MissingPermission")
    public boolean sendData(String data) {
        if (writeCharacteristic != null && bluetoothGatt != null) {
            writeCharacteristic.setValue(data.getBytes());
            boolean success = bluetoothGatt.writeCharacteristic(writeCharacteristic);
            Log.d(TAG, "Sending data: " + data + ", success: " + success);
            return success;
        } else {
            Log.e(TAG, "writeCharacteristic is null or bluetoothGatt is null");
            return false;
        }
    }

    public boolean isConnected() {
        return bluetoothGatt != null && writeCharacteristic != null;
    }
    @SuppressLint("MissingPermission")
    public void disconnect() {
        if (bluetoothGatt != null) {
            bluetoothGatt.disconnect();
            bluetoothGatt.close();
            bluetoothGatt = null;
            writeCharacteristic = null;
            Log.d(TAG, "Disconnected from device");
        }
    }
}
