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
import java.net.Socket;
import java.util.Arrays;
import java.util.UUID;

import tech.yaog.utils.aioclient.io.IO;

public class BluetoothSppServerIO extends IO {

    private String mac;

    private BluetoothSocket socket;

    private BluetoothServerSocket serverSocket;

    private Thread readThread = null;

    private Thread acceptThread = null;

    private final Object acceptLock = new Object();

    public BluetoothSppServerIO(Callback callback) {
        super(callback);
    }

    @SuppressLint("MissingPermission")
    @Override
    public boolean connect(String remote) {
        mac = remote;
        try {
            serverSocket = BluetoothAdapter.getDefaultAdapter().listenUsingRfcommWithServiceRecord("SPP", UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));

            acceptThread = new Thread(() -> {
                while (!Thread.interrupted()) {
                    try {
                        BluetoothSocket tmp = serverSocket.accept(connTimeout);
                        if (tmp.getRemoteDevice().getAddress().equalsIgnoreCase(mac)) {
                            socket = tmp;
                            break;
                        }
                        else {
                            tmp.close();
                        }
                    } catch (IOException e) {
                        break;
                    }
                }
                synchronized (acceptLock) {
                    acceptLock.notifyAll();
                }
            });
            acceptThread.start();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    Thread.currentThread().setName("Connect Notify");
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
//                    try {
//                        Method method = BluetoothDevice.class.getMethod("createRfcommSocket", int.class);
                        try (BluetoothSocket socket1 = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(mac).createInsecureRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"))) {
                            Log.d("SPPServer", "Notify connect");
                            socket1.connect();
                            Thread.sleep(1000);
                        }
                        catch (Exception ignored) {
                        }
//                    } catch (NoSuchMethodException e) {
//                        throw new RuntimeException(e);
//                    }
                }
            }).start();

            synchronized (acceptLock) {
                try {
                    acceptLock.wait();
                } catch (InterruptedException ignored) {
                }
            }
            if (socket != null) {
                callback.onConnected();
                return true;
            }
            else {
                callback.onDisconnected();
            }

        } catch (IOException e) {
            callback.onException(e);
            callback.onDisconnected();
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
        readThread.setName("SPPServer("+mac+") Reader");
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
