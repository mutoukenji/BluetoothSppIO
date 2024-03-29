package tech.yaog.bluetoothsppio.lib;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.UUID;

import tech.yaog.utils.aioclient.io.IO;

public class BluetoothSppIO extends IO {

    private String mac;

    private BluetoothSocket socket;

    private Thread readThread = null;

    private boolean isConnecting = false;

    public BluetoothSppIO(Callback callback) {
        super(callback);
    }

    @SuppressLint("MissingPermission")
    @Override
    public boolean connect(String remote) {
        String[] parts = remote.split("/");
        boolean secure = true;
        boolean force = false;
        if (parts.length > 1) {
            String part1 = parts[1];
            if ("insecure".equals(part1)) {
                secure = false;
            }
            else if ("force".equals(part1)) {
                force = true;
            }
        }
        mac = parts[0];
        BluetoothDevice device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(mac);
        try {
            if (force) {
                Method method = device.getClass().getMethod("createRfcommSocket", int.class);
                socket = (BluetoothSocket) method.invoke(device, 1);
            }
            else if (secure) {
                socket = device.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
            }
            else {
                socket = device.createInsecureRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
            }

            new Thread(new Runnable() {
                @Override
                public void run() {
                    Thread.currentThread().setName("BTConnTimer");
                    try {
                        Thread.sleep(connTimeout);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    if (socket != null && isConnecting) {
                        try {
                            socket.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }).start();

            isConnecting = true;
            socket.connect();
            isConnecting = false;
            callback.onConnected();
            return true;
        } catch (IOException e) {
            callback.onDisconnected();
            callback.onException(e);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        finally {
            isConnecting = false;
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
