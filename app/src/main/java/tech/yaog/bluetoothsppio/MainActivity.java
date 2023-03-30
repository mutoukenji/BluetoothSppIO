package tech.yaog.bluetoothsppio;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.contract.ActivityResultContract;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import permissions.dispatcher.NeedsPermission;
import permissions.dispatcher.RuntimePermissions;
import tech.yaog.bluetoothsppio.databinding.ActivityMainBinding;

@RuntimePermissions
public class MainActivity extends AppCompatActivity {

    ActivityMainBinding binding;

    private static final int DISCONNECT = 0;
    private static final int CONNECTING = 1;
    private static final int CONNECT = 2;

    private int connectState = DISCONNECT;

    private Handler handler;

    private SPPTest sppTest;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        handler = new Handler(Looper.myLooper()) {
            @Override
            public void handleMessage(@NonNull Message msg) {
                if (msg.what == 1) {
                    Log.d("MainActivity", "update button "+connectState);
                    if (connectState == DISCONNECT) {
                        binding.connect.setText("Connect");
                        binding.connect.setEnabled(true);
                        binding.connect.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                MainActivityPermissionsDispatcher.checkAndConnectWithPermissionCheck(MainActivity.this);
                            }
                        });
                    }
                    else if (connectState == CONNECTING) {
                        binding.connect.setText("...");
                        binding.connect.setEnabled(false);
                    }
                    else if (connectState == CONNECT) {
                        binding.connect.setText("Disconnect");
                        binding.connect.setEnabled(true);
                        binding.connect.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                disconnect();
                            }
                        });
                    }
                }
            }
        };

        handler.sendEmptyMessage(1);

    }

    @SuppressLint("MissingPermission")
    @NeedsPermission({Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_COARSE_LOCATION})
    void checkAndConnect() {
        if(BluetoothAdapter.getDefaultAdapter() == null ||!BluetoothAdapter.getDefaultAdapter().isEnabled()){
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {

                }
            }).launch(enableBtIntent);
            return;
        }
        Set<BluetoothDevice> bondDevices = BluetoothAdapter.getDefaultAdapter().getBondedDevices();
        for (BluetoothDevice bluetoothDevice : bondDevices) {
            if ("CM-8828".equals(bluetoothDevice.getName())) {
                connect(bluetoothDevice);
                return;
            }
        }
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothDevice.ACTION_FOUND);
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        final List<BluetoothDevice> fondDevices = new ArrayList<>();
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals(BluetoothDevice.ACTION_FOUND)) {
                    BluetoothDevice device=intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if(!fondDevices.contains(device)){//去重
                        fondDevices.add(device);
                    }
                }
                else if (intent.getAction().equals(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)) {
                    for (BluetoothDevice bluetoothDevice : fondDevices) {
                        if ("CM-8828".equals(bluetoothDevice.getName())) {
                            connect(bluetoothDevice);
                            break;
                        }
                    }
                }
            }
        };
        registerReceiver(receiver, intentFilter);
        BluetoothAdapter.getDefaultAdapter().startDiscovery();
    }

    private void disconnect() {
        sppTest.disconnect();
    }

    private void connect(BluetoothDevice bluetoothDevice) {
        connectState = CONNECTING;
        handler.sendEmptyMessage(1);

        sppTest = new SPPTest(bluetoothDevice.getAddress());
        sppTest.setEventListener(new SPPTest.Event() {
            @Override
            public void onConnected() {
                if (connectState == CONNECTING) {
                    connectState = CONNECT;
                    handler.sendEmptyMessage(1);
                }
            }

            @Override
            public void onDisConnected() {
                if (connectState == CONNECT) {
                    connectState = DISCONNECT;
                    handler.sendEmptyMessage(1);
                }
                if (connectState == CONNECTING) {
                    connectState = DISCONNECT;
                    Log.e("MainActivity", "Connect Failed");
                    handler.sendEmptyMessage(1);
                }
            }

            @Override
            public void onReceive(String msg) {

            }
        });
        sppTest.connect();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        MainActivityPermissionsDispatcher.onRequestPermissionsResult(this, requestCode, grantResults);
    }
}