package tech.yaog.bluetoothsppio.lib;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.UUID;

import tech.yaog.utils.aioclient.io.IO;

public class BluetoothSppIO extends IO {

    private String mac;

    private BluetoothSocket socket;

    private Thread readThread = null;

    public BluetoothSppIO(Callback callback) {
        super(callback);
    }

    @SuppressLint("MissingPermission")
    @Override
    public boolean connect(String remote) {
        mac = remote;
        BluetoothDevice device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(mac);
        try {
            socket = device.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
            socket.connect();
            callback.onConnected();
        } catch (IOException e) {
            callback.onDisconnected();
            callback.onException(e);
        }
        return false;
    }

    @Override
    public void disconnect() {
        stopRead();
    }

    @Override
    public void beginRead() {
        readThread = new Thread() {
            @Override
            public void run() {
                try (InputStream is = socket.getInputStream()){
                    byte[] buff = new byte[10240];
                    int readLength;
                    while (!Thread.interrupted()) {
                        if ((readLength = is.read(buff)) > 0) {
                            Log.d("SPP", "Read length:" +readLength);
                            callback.onReceived(Arrays.copyOf(buff, readLength));
                        }
                        else if (readLength < 0) {
                            Log.d("SPP", "Socket broken");
                            break;
                        }
                    }
                } catch (IOException e) {
                    callback.onException(e);
                }
                Log.d("SPP", "DisConnect");
                callback.onDisconnected();
            }
        };
        readThread.setName("SPP("+mac+") Reader");
        readThread.setPriority(8);
        readThread.start();
    }

    @Override
    public void stopRead() {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
                callback.onException(e);
            }
        }
        socket = null;
        if (readThread != null) {
            readThread.interrupt();
        }
        readThread = null;
    }

    @Override
    public void write(byte[] bytes) {
        if (socket != null) {
            try {
                socket.getOutputStream().write(bytes);
            } catch (IOException e) {
                callback.onException(e);
            }
        }
    }
}
