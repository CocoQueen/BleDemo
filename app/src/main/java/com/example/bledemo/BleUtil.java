package com.example.bledemo;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.RequiresApi;

import com.clj.fastble.BleManager;
import com.clj.fastble.callback.BleGattCallback;
import com.clj.fastble.callback.BleIndicateCallback;
import com.clj.fastble.callback.BleNotifyCallback;
import com.clj.fastble.callback.BleScanCallback;
import com.clj.fastble.data.BleDevice;
import com.clj.fastble.exception.BleException;
import com.clj.fastble.scan.BleScanRuleConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class BleUtil {
    private static final String TAG = "BleUtil-->";
    //E0:7D:EA:57:EB:92
    private static BleUtil bleUtil;
    public List<BleDevice> bleDevices;
    private OnBleListener listener;
    private String uuid_service = "0000c0e0-0000-1000-8000-00805f9b34fb";
    //接受数据特征UUID
    private String uuid_characteristic_receive = "0000c0e1-0000-1000-8000-00805f9b34fb";

//    private String uuid_service = "49535343-fe7d-4ae5-8fa9-9fafd205e455";
//    //接受数据特征UUID
//    private String uuid_characteristic_receive = "49535343-1e4d-4bd9-ba61-23c647249616";

    //发送数据特征UUID
//    private String uuid_characteristic_send = "49535343-aca3-481c-91ec-d85e28a60318";
    private boolean isStandByBle;
    private boolean isEnableBle;
    private Context context;
    private Handler handler = new Handler(Looper.getMainLooper());
    private final int START_SCAN = 100;
    private final int RESET_CONNECT = 101;
    //    private final UUID[] serviceUuids;
    private BleDevice connectedBleDevice;
    private BleScanRunnable bleScanRunnable;
    private BleResetConnectRunnable bleConnectRunnable;
    private BleManager bleManager;
    private BleConnectedRunnable bleConnectedRunnable;
    private boolean isResetConnect = false;
    private boolean isScaning;
    private final ReturnTimeOutRunnable returnTimeOutRunnable;
    private String currentData = "";
    private final ReceiveDataRunnable receiveDataRunnable;

    private BleUtil(Context context) {
        this.context = context.getApplicationContext();
        bleManager = BleManager.getInstance();
        isStandByBle = bleManager.isSupportBle();
        isEnableBle = bleManager.isBlueEnable();
//        //根据指定的UUID扫描特定的设备
//        UUID serviceUuid = UUID.fromString(uuid_service);
//        serviceUuids = new UUID[]{serviceUuid};
        bleScanRunnable = new BleScanRunnable();
        bleConnectRunnable = new BleResetConnectRunnable();
        bleConnectedRunnable = new BleConnectedRunnable();
        returnTimeOutRunnable = new ReturnTimeOutRunnable();
        receiveDataRunnable = new ReceiveDataRunnable();
    }

    public static BleUtil getInstance(Context context) {
        if (bleUtil == null) {
            synchronized (BleUtil.class) {
                if (bleUtil == null) {
                    bleUtil = new BleUtil(context);
                }
            }
        }
        return bleUtil;
    }

    /**
     * serviceUuidStr = "1b7e8251-2877-41c3-b46e-cf057c562023";
     * writeCharactUuid = "5e9bf2a8-f93f-4481-a67e-3b2f4a07891a";
     * notifyCharactUuid = "8ac32d3f-5cb9-4d44-bec2-ee689169f626";
     */

    public void startBle() {
        if (!isStandByBle) {
            Toast.makeText(context, "该设备不支持蓝牙功能", Toast.LENGTH_SHORT).show();
            return;
        }
        bleDevices = new ArrayList<>();
        BleScanRuleConfig scanRuleConfig = new BleScanRuleConfig.Builder()
//                .setServiceUuids(serviceUuids)
//                .setAutoConnect(true)
//                .setDeviceMac("00:0C:BF:18:76:33")
                .setScanTimeOut(15000)
                .build();
        bleManager.initScanRule(scanRuleConfig);
        if (!bleManager.isBlueEnable()) {
            bleManager.enableBluetooth();
        }
        handler.postDelayed(bleScanRunnable, 2 * 100);
    }

    private void startScan() {
        if (isResetConnect && listener != null) {
            listener.onResetConnect();
            isResetConnect = false;
        }
        bleManager.scan(new BleScanCallback() {

            @Override
            public void onScanFinished(List<BleDevice> list) {
                isScaning = false;
            }

            @Override
            public void onScanStarted(boolean b) {
                isScaning = true;
            }

            @Override
            public void onScanning(BleDevice bleDevice) {
                Log.e(TAG, bleDevice.getName() + "        " + bleDevice.getMac());
                bleDevices.add(bleDevice);
                if (listener != null) {
                    listener.onScaningBle(bleDevice);
                }
            }
        });
    }

    public void stopScan() {
        if (isScaning)
            bleManager.cancelScan();
    }

    public void disConnect() {
        handler.removeCallbacks(bleScanRunnable);
        handler.removeCallbacks(bleConnectedRunnable);
        handler.removeCallbacks(bleConnectRunnable);
        handler.removeCallbacks(returnTimeOutRunnable);
        handler.removeCallbacks(receiveDataRunnable);
        if (connectedBleDevice != null && bleManager.isConnected(connectedBleDevice)) {
            stopIndicate();
            bleManager.clearCharacterCallback(connectedBleDevice);
            bleManager.disconnect(connectedBleDevice);
        }
    }

    public boolean isConnected() {
        if (connectedBleDevice == null) {
            return false;
        } else {
            return bleManager.isConnected(connectedBleDevice);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    public void connectBle(BleDevice bleDevice) {
        stopScan();
        bleManager.connect(bleDevice, new BleGattCallback() {
            @Override
            public void onStartConnect() {

            }

            @Override
            public void onConnectFail(BleDevice bleDevice, BleException e) {
                //连接失败，需做好重连措施
                connectedBleDevice = bleDevice;
                handler.postDelayed(bleConnectRunnable, 200);
                Log.e("连接失败：", e.toString());
            }

            @Override
            public void onConnectSuccess(BleDevice bleDevice, BluetoothGatt bluetoothGatt, int i) {
                Log.e(TAG, "连接成功");
                openNotify(bleDevice);
//                receiveData(bleDevice);
                connectedBleDevice = bleDevice;
                handler.postDelayed(bleConnectedRunnable, 200);
                //设备的服务信息及特征信息
                List<BluetoothGattService> serviceList = bluetoothGatt.getServices();
                for (BluetoothGattService service : serviceList) {
                    UUID uuid_service = service.getUuid();
                    Log.e(TAG, "onConnectSuccess:service---- " + uuid_service);
                    List<BluetoothGattCharacteristic> characteristicList = service.getCharacteristics();
                    for (BluetoothGattCharacteristic characteristic : characteristicList) {
                        UUID uuid_chara = characteristic.getUuid();
                        Log.e(TAG, "onConnectSuccess: chara---" + uuid_chara);
                    }
                }
            }

            @Override
            public void onDisConnected(boolean b, BleDevice bleDevice, BluetoothGatt bluetoothGatt, int i) {
                //连接断开，需区分异常断开与主动断开(b=true)，异常断开的重连操作，需做好时间间隔操作，否者可能导致长时间连接不上的情况
                if (b) {
                    Log.e(TAG, "正常断开");
                    bleManager.clearCharacterCallback(bleDevice);
                    bluetoothGatt.connect();
                    bleManager.clearCharacterCallback(connectedBleDevice);
                    if (listener != null) {
                        listener.onDisConnected();
                    }
                } else {
                    isResetConnect = true;
                    Log.e(TAG, "异常断开");
                    if (!bleManager.isBlueEnable()) {
                        bleManager.enableBluetooth();
                        handler.postDelayed(bleScanRunnable, 200);
                    } else {
                        //重连
                        handler.postDelayed(bleConnectRunnable, 200);
                    }
                }
            }
        });
    }

    public void connectBle(String MAC) {
        bleManager.connect(MAC, new BleGattCallback() {
            @Override
            public void onStartConnect() {

            }

            @Override
            public void onConnectFail(BleDevice bleDevice, BleException e) {
                //连接失败，需做好重连措施
                connectedBleDevice = bleDevice;
                handler.postDelayed(bleConnectRunnable, 200);
                Log.e("连接失败：", e.toString());
            }

            @Override
            public void onConnectSuccess(BleDevice bleDevice, BluetoothGatt gatt, int status) {
                Log.e(TAG, "连接成功");
                receiveData(bleDevice);
                connectedBleDevice = bleDevice;
                handler.postDelayed(bleConnectedRunnable, 200);
            }

            @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
            @Override
            public void onDisConnected(boolean b, BleDevice device, BluetoothGatt gatt, int status) {
                //连接断开，需区分异常断开与主动断开(b=true)，异常断开的重连操作，需做好时间间隔操作，否者可能导致长时间连接不上的情况
                if (b) {
                    Log.e(TAG, "正常断开");
                    bleManager.clearCharacterCallback(device);
                    gatt.connect();
                    bleManager.clearCharacterCallback(connectedBleDevice);
                    if (listener != null) {
                        listener.onDisConnected();
                    }
                } else {
                    isResetConnect = true;
                    Log.e(TAG, "异常断开");
                    if (!bleManager.isBlueEnable()) {
                        bleManager.enableBluetooth();
                        handler.postDelayed(bleScanRunnable, 200);
                    } else {
                        //重连
                        handler.postDelayed(bleConnectRunnable, 200);
                    }
                }
            }
        });

    }

    private void receiveData(final BleDevice bleDevice) {
        final StringBuilder stringBuilder = new StringBuilder();
        bleManager.indicate(bleDevice,
                uuid_service,
                uuid_characteristic_receive,
                new BleIndicateCallback() {
                    @Override
                    public void onIndicateSuccess() {
                        //订阅通知成功
                        handler.postDelayed(returnTimeOutRunnable, 5 * 1000);
                        Log.e(TAG, "onIndicateSuccess: 订阅成功");
                    }

                    @Override
                    public void onIndicateFailure(BleException e) {
                        Log.e("接收数据异常------------>", e.toString());
                    }

                    @Override
                    public void onCharacteristicChanged(byte[] bytes) {
                        handler.removeCallbacks(returnTimeOutRunnable);
                        //接收到的数据
                        String s = BinaryConversionUtils.byte2hex(bytes);
                        String resultData = BinaryConversionUtils.hexString2String(s);
                        Pattern pattern = Pattern.compile("\n|\r");
                        Matcher matcher = pattern.matcher(resultData);
                        resultData = matcher.replaceAll("");
                        stringBuilder.append(resultData);
                        Log.e("接收数据成功------------>", stringBuilder.toString());
//                        Toast.makeText(context, resultData+"--", Toast.LENGTH_SHORT).show();
                        if (listener != null) {
                            if (TextUtils.isEmpty(stringBuilder.toString()) || stringBuilder.toString().contains("ERROR")) {
                                //空返回
                                handler.postDelayed(returnTimeOutRunnable, 200);
                            } else if (resultData.contains("")) {
                                //成功返回
                                currentData = resultData;
                                handler.postDelayed(receiveDataRunnable, 200);
//                                stopIndicate();
                            }
                        }
                    }
                });
    }

//    public void sendData(final BleDevice bleDevice, final String str) {
//        byte[] data = BinaryConversionUtils.hex2byte(str);
//        bleManager.write(bleDevice,
//                uuid_service,
//                uuid_characteristic_send,
//                data,
//                true,
//                new BleWriteCallback() {
//                    @Override
//                    public void onWriteSuccess(int current, int total, byte[] justWrite) {
//                        // 发送数据到设备成功（分包发送的情况下，可以通过方法中返回的参数可以查看发送进度）
//                        Log.e("发送数据成功------------>", str);
//                        receiveData(bleDevice);
//                        bleManager.removeWriteCallback(bleDevice, uuid_characteristic_send);
//                    }
//
//                    @Override
//                    public void onWriteFailure(BleException exception) {
//                        // 发送数据到设备失败
//                        Log.e("发送数据异常------------>", exception.toString());
//                    }
//                });
//    }

    public void stopIndicate() {
        if (connectedBleDevice != null) {
            bleManager.stopIndicate(connectedBleDevice, uuid_service, uuid_characteristic_receive);
            bleManager.removeIndicateCallback(connectedBleDevice, uuid_characteristic_receive);
        }
    }

    //扫描设备的实时回调
    public interface OnBleListener {

        //扫描结果
        void onScaningBle(BleDevice bleDevice);

        //连接成功
        void onConnected(BleDevice bleDevice);

        //异常重连
        void onResetConnect();

        //返回数据
        void onReceiveData(String data);

        //返回数据超时
        void onTimeOutReturn();

        //蓝牙正常断开
        void onDisConnected();
    }

    public void setOnBleListener(OnBleListener listener) {
        this.listener = listener;
    }

    public class BleScanRunnable implements Runnable {
        @Override
        public void run() {
            startScan();
        }
    }

    public class BleResetConnectRunnable implements Runnable {
        @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
        @Override
        public void run() {
            if (connectedBleDevice != null) {
                if (listener != null)
                    listener.onResetConnect();
                connectBle(connectedBleDevice);
            } else {
                Toast.makeText(context, "未扫描到蓝牙，请退出重连", Toast.LENGTH_SHORT).show();
            }
        }
    }

    public class BleConnectedRunnable implements Runnable {
        @Override
        public void run() {
            if (listener != null)
                listener.onConnected(connectedBleDevice);
        }
    }

    public class ReturnTimeOutRunnable implements Runnable {
        @Override
        public void run() {
            if (listener != null) {
                listener.onTimeOutReturn();
            }
        }
    }

    public class ReceiveDataRunnable implements Runnable {
        @Override
        public void run() {
            if (listener != null) {
                listener.onReceiveData(currentData);
            }
        }
    }

    public void openNotify(BleDevice bleDevice) {
        BleManager.getInstance().notify(
                bleDevice,
                uuid_service,
                uuid_characteristic_receive,
                new BleNotifyCallback() {
                    @Override
                    public void onNotifySuccess() {
                        Log.e(TAG, "onNotifySuccess: 打开通知成功");
                        // 打开通知操作成功
                    }

                    @Override
                    public void onNotifyFailure(BleException exception) {
                        // 打开通知操作失败
                        Log.e(TAG, "onNotifySuccess: 打开通知失败" + exception.getDescription());
                    }

                    @Override
                    public void onCharacteristicChanged(byte[] data) {
                        // 打开通知后，设备发过来的数据将在这里出现

                        Log.e(TAG, "onCharacteristicChanged: " + data);
                    }
                });
    }

}
