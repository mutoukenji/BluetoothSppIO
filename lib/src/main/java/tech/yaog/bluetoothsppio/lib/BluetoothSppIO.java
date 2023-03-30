package tech.yaog.bluetoothsppio.lib;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

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
            throw new RuntimeException(e);
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
                        if (is.available() > 0) {
                            if ((readLength = is.read(buff)) > 0) {
                                callback.onReceived(Arrays.copyOf(buff, readLength));
                            }
                        }
                    }
                } catch (IOException e) {
                    callback.onException(e);
                }
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
