package tech.yaog.bluetoothsppio;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.contract.ActivityResultContract;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelUuid;
import android.os.Parcelable;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

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

//    private SPPTest sppTest;

    private OBDTerminal obdTerminal;

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
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    MainActivityPermissionsDispatcher.checkAndConnectAfterSWithPermissionCheck(MainActivity.this);
                                }
                                else {
                                    MainActivityPermissionsDispatcher.checkAndConnectWithPermissionCheck(MainActivity.this);
                                }
                            }
                        });
                        binding.edtInput.setOnEditorActionListener(null);
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
                        binding.edtInput.setOnEditorActionListener((TextView.OnEditorActionListener) (v, actionId, event) -> {
                            if (actionId == EditorInfo.IME_ACTION_SEND ||
                                    actionId == EditorInfo.IME_ACTION_GO ||
                                    actionId == EditorInfo.IME_ACTION_DONE ||
                                    event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                                sendMessageToOBD(binding.edtInput.getText().toString());
                                binding.txtContent.setText(binding.txtContent.getText()+binding.edtInput.getText().toString()+"\n");
                                binding.edtInput.setText("");
                                return true;
                            }
                            return false;
                        });
                    }
                }
            }
        };

        handler.sendEmptyMessage(1);

    }

    private void sendMessageToOBD(String message) {
        if (message.trim().isEmpty()) {

        }
        else {
            obdTerminal.send(message);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    @NeedsPermission({Manifest.permission.BLUETOOTH_CONNECT})
    void checkAndConnectAfterS() {
        checkAndConnect();
    }

    @SuppressLint("MissingPermission")
    @NeedsPermission({Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.BLUETOOTH, Manifest.permission.ACCESS_COARSE_LOCATION})
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
            if ("OBDII".equals(bluetoothDevice.getName())) {
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
                        if ("OBDII".equals(bluetoothDevice.getName())) {
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
        obdTerminal.disconnect();
    }

    private final Object uuidLock = new Object();

    private BroadcastReceiver uuidReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(BluetoothDevice.ACTION_UUID)) {
                Parcelable[] uuids = intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID);
                if (uuids != null) {
                    for (Parcelable uuid : uuids) {
                        ParcelUuid pUUid = (ParcelUuid) uuid;
                        UUID realUuid = pUUid.getUuid();
                        Log.i("MainActivity", "uuid: "+realUuid.toString());
                    }
                }
                else {
                    Log.e("MainActivity", "uuids is null!!!");
                }
                synchronized (uuidLock) {
                    uuidLock.notifyAll();
                }
            }
        }
    };

    private void connect(BluetoothDevice bluetoothDevice) {
        connectState = CONNECTING;
        handler.sendEmptyMessage(1);

        new Thread(new Runnable() {
            @SuppressLint("MissingPermission")
            @Override
            public void run() {

//                registerReceiver(uuidReceiver, new IntentFilter(BluetoothDevice.ACTION_UUID));
//                bluetoothDevice.fetchUuidsWithSdp();
//                synchronized (uuidLock) {
//                    try {
//                        uuidLock.wait();
//                    } catch (InterruptedException ignored) {
//                    }
//                }
//                unregisterReceiver(uuidReceiver);

                obdTerminal = new OBDTerminal(bluetoothDevice.getAddress());
                obdTerminal.setEventListener(new OBDTerminal.Event() {
                    @Override
                    public void onConnected() {
                        if (connectState == CONNECTING) {
                            Log.i("MainActivity", "Connected");
                            connectState = CONNECT;
                            handler.sendEmptyMessage(1);
                        }
                    }

                    @Override
                    public void onDisconnected() {
                        if (connectState == CONNECT) {
                            connectState = DISCONNECT;
                            handler.sendEmptyMessage(1);
                        }
                    }

                    @Override
                    public void onNewMessage(String message) {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                binding.txtContent.setText(binding.txtContent.getText() + message);
                            }
                        });
                    }
                });
                if(!obdTerminal.connect()) {
                    Log.e("MainActivity", "Connect Failed 1");
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                Thread.sleep(1000);
                                boolean forceConnect = obdTerminal.connect("force");
                                if (!forceConnect) {
                                    Log.e("MainActivity", "Connect Failed 2");
                                    connectState = DISCONNECT;
                                    handler.sendEmptyMessage(1);
                                }
                            } catch (InterruptedException e) {
                            }
                        }
                    }).start();
                }
            }
        }).start();

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        MainActivityPermissionsDispatcher.onRequestPermissionsResult(this, requestCode, grantResults);
    }
}