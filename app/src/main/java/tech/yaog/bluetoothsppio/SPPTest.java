package tech.yaog.bluetoothsppio;

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import tech.yaog.bluetoothsppio.lib.BluetoothSppIO;
import tech.yaog.utils.aioclient.AbstractDecoder;
import tech.yaog.utils.aioclient.AbstractHandler;
import tech.yaog.utils.aioclient.AbstractSplitter;
import tech.yaog.utils.aioclient.Bootstrap;

public class SPPTest {

    private HandlerThread handlerThread;
    private Handler handler;

    private String mac;

    private Bootstrap bootstrap;

    public void setEventListener(Event eventListener) {
        this.eventListener = eventListener;
    }

    public interface Event {
        void onConnected();
        void onDisConnected();
        void onReceive(String msg);
    }

    private Event eventListener = null;

    public SPPTest(String mac) {
        this.mac = mac;
        init();
        handlerThread = new HandlerThread("SPPTest");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
    }

    public void connect() {
        handler.post(() -> bootstrap.connect(mac));

    }

    public void disconnect() {
        handler.post(() -> bootstrap.disconnect());
    }

    private static final char[] CHARS = new char[]{'0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F'};

    public static String hexToString(byte[] bytes) {
        StringBuilder builder = new StringBuilder();
        boolean lead = true;
        for (byte b : bytes) {
            if (!lead) {
                builder.append(' ');
            }
            lead = false;
            int h = b / 0x10;
            int l = b % 0x10;
            builder.append(CHARS[h]);
            builder.append(CHARS[l]);
        }
        return builder.toString();
    }

    private void init() {
        bootstrap = new Bootstrap()
                .ioClass(BluetoothSppIO.class)
                .splitter(new AbstractSplitter() {
                    @Override
                    public void split(byte[] raw) {
                        for (int i = 0;i<raw.length - 10;i++) {
                            if (raw[i] == 0x10 && raw[i+1] == 0x08) {
                                callback.newFrame(i, 11, 0);
                                return;
                            }
                        }
                        callback.newFrame(0, raw.length - 10);
                    }
                })
                .decoders(new AbstractDecoder<String>() {
                    @Override
                    public String decode(byte[] byteBuffer) {
                        return hexToString(byteBuffer);
                    }
                })
                .handlers(new AbstractHandler<String>() {
                    @Override
                    public boolean handle(String msg) {
                        Log.d("Rx", msg);
                        eventListener.onReceive(msg);
                        return true;
                    }
                })
                .onEvent(new Bootstrap.Event() {
                    @Override
                    public void onConnected() {
                        Log.d("SPP", "Connected");
                        eventListener.onConnected();
                    }

                    @Override
                    public void onDisconnected() {
                        Log.d("SPP", "DisConnected");
                        eventListener.onDisConnected();
                    }

                    @Override
                    public void onSent() {
                        Log.d("SPP", "onSent");
                    }

                    @Override
                    public void onReceived() {
                        Log.d("SPP", "onReceived");
                    }
                })
                .exceptionHandler(new Bootstrap.ExceptionHandler() {
                    @Override
                    public void onExceptionTriggered(Throwable t) {
                        t.printStackTrace();
                    }
                });
    }
}
