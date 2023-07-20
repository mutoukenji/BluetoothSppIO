package tech.yaog.bluetoothsppio;

import android.util.Log;

import java.nio.charset.StandardCharsets;

import tech.yaog.bluetoothsppio.lib.BluetoothSppIO;
import tech.yaog.bluetoothsppio.lib.BluetoothSppServerIO;
import tech.yaog.utils.aioclient.AbstractHandler;
import tech.yaog.utils.aioclient.AbstractSplitter;
import tech.yaog.utils.aioclient.Bootstrap;
import tech.yaog.utils.aioclient.StringDecoder;
import tech.yaog.utils.aioclient.encoder.StringEncoder;

public class OBDTerminal {

    private static final String TAG = OBDTerminal.class.getSimpleName();

    private String mac;
    private Bootstrap bootstrap;

    private Event eventListener;

    public Event getEventListener() {
        return eventListener;
    }

    public void setEventListener(Event eventListener) {
        this.eventListener = eventListener;
    }

    public interface Event {
        void onConnected();
        void onDisconnected();
        void onNewMessage(String message);
    }

    public OBDTerminal(String mac) {
        this.mac = mac;
    }

    public boolean connect() {
        return connect("");
    }



    public boolean connect(String type) {
        bootstrap = new Bootstrap()
                .decoders(new StringDecoder(StandardCharsets.UTF_8))
                .encoders(new StringEncoder(StandardCharsets.UTF_8))
                .splitter(new AbstractSplitter() {
                    @Override
                    public void split(byte[] raw) {
                        Log.d(TAG, "to split: "+SPPTest.hexToString(raw));
                        for (int i = 0;i<raw.length;i++) {
                            if (raw[i] == '\r' || raw[i] == (byte)'>') {
                                callback.newFrame(i+1);
                                return;
                            }
                        }
                    }
                })
                .handlers(new AbstractHandler<String>() {
                    @Override
                    public boolean handle(String msg) {
                        String rx = msg.replace('\r', '\n');
                        System.out.println("Rx: "+rx);
                        if (eventListener != null) {
                            eventListener.onNewMessage(rx);
                        }
                        return true;
                    }
                })
                .connTimeout(10000)
                .ioClass(BluetoothSppIO.class)
                .onEvent(new Bootstrap.Event() {
                    @Override
                    public void onConnected() {
                        Log.d(TAG, "onConnected");
                        if (eventListener != null) {
                            eventListener.onConnected();
                        }
                    }

                    @Override
                    public void onDisconnected() {
                        Log.d(TAG, "onDisconnected");
                        if (eventListener != null) {
                            eventListener.onDisconnected();
                        }
                    }

                    @Override
                    public void onSent() {
                        Log.d(TAG, "sent");
                    }

                    @Override
                    public void onReceived() {
                        Log.d(TAG, "recv");
                    }
                })
                .exceptionHandler(new Bootstrap.ExceptionHandler() {
                    @Override
                    public void onExceptionTriggered(Throwable t) {
                        t.printStackTrace();
                    }
                });
        return bootstrap.connect(mac+"/"+type);
    }

    public void send(String msg) {
        System.out.println("Tx: "+msg);
        String tx = msg.replace('\n', '\r');
        if (!tx.endsWith("\r")) {
            tx += "\r";
        }
        bootstrap.send(tx);
    }

    public void disconnect() {
        bootstrap.disconnect();
    }
}
