package com.fidesmo.ble.client;

import android.Manifest;
import android.annotation.TargetApi;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.os.Build;
import android.support.annotation.RequiresPermission;
import com.fidesmo.ble.client.protocol.SimplePacketFragmenter;
import nordpol.IsoCard;
import nordpol.OnCardErrorListener;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class BleCard implements IsoCard, Closeable {
    private static final String TAG = BleCard.class.getName();

    public static final UUID APDU_SERVICE_UUID = UUID.fromString("8e790d52-bb90-4967-a4a5-3f21aa9e05eb");
    public static final UUID APDU_WRITE_CHARACTERISTIC_UUID = UUID.fromString("8e79ecae-bb90-4967-a4a5-3f21aa9e05eb");
    public static final UUID APDU_CONVERSATION_FINISHED_CHARACTERISTIC_UUID = UUID.fromString("8e798746-bb90-4967-a4a5-3f21aa9e05eb");
    public static final UUID APDU_RESPONSE_READY_NOTIFY_CHARACTERISTIC_UUID = UUID.fromString("8e795e92-bb90-4967-a4a5-3f21aa9e05eb");
    public static final UUID APDU_READ_CHARACTERISTIC_UUID = UUID.fromString("8e7927a7-bb90-4967-a4a5-3f21aa9e05eb");
    public static final UUID APDU_MAX_MEMORY_FOR_APDU_PROCESSING = UUID.fromString("8e79e13b-bb90-4967-a4a5-3f21aa9e05eb");

    private BleGattServiceClient gattClient;

    private int timeout = 120000;
    private int transceiveLength = 512;
    private boolean connected = false;

    private List<OnCardErrorListener> errorListeners = new CopyOnWriteArrayList();

    @RequiresPermission(Manifest.permission.BLUETOOTH_ADMIN)
    public BleCard(Context context, BluetoothDevice device) {
        gattClient = new BleGattServiceClient(context,
                device,
                SimplePacketFragmenter.factory(),
                transceiveLength);
    }

    @Override
    public void addOnCardErrorListener(OnCardErrorListener onCardErrorListener) {
        errorListeners.add(onCardErrorListener);
    }

    @Override
    public void removeOnCardErrorListener(OnCardErrorListener onCardErrorListener) {
        errorListeners.remove(onCardErrorListener);
    }

    @Override
    public void close() throws IOException {
        gattClient.send(new byte[]{0,0,0,0}, APDU_SERVICE_UUID, APDU_CONVERSATION_FINISHED_CHARACTERISTIC_UUID);
        gattClient.close();
    }

    @Override
    public void connect() throws IOException {

        try {
            final CountDownLatch connectionLatch = new CountDownLatch(1);

            gattClient.connect(true, new BleConnectionStatusListener() {
                @Override
                public void connectionEstablished() {
                    connectionLatch.countDown();
                }

                @Override
                public void connectionFailed() {
                    connectionLatch.countDown();
                }
            });

            connectionLatch.await(timeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            throw new IOException(e);
        }

        readMaxApduSequenceSize();
    }

    /**
     * Obtains max APDU sequence size that can fit into device memory.
     */
    private void readMaxApduSequenceSize() {
        try {
            byte[] buffer = gattClient.read(APDU_SERVICE_UUID, APDU_MAX_MEMORY_FOR_APDU_PROCESSING).get(2, TimeUnit.MINUTES);
            transceiveLength = BleUtils.unpackInt4(buffer, 0);
        } catch (Exception ex) {
            transceiveLength = Integer.MAX_VALUE;
        }
    }

    @Override
    public int getMaxTransceiveLength() throws IOException {
        return transceiveLength;
    }

    @Override
    public int getTimeout() {
        return timeout;
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    @Override
    public byte[] transceive(byte[] command) throws IOException {
        try {
            return gattClient.sendReceive(command,
                    APDU_SERVICE_UUID,
                    APDU_WRITE_CHARACTERISTIC_UUID,
                    APDU_RESPONSE_READY_NOTIFY_CHARACTERISTIC_UUID,
                    APDU_READ_CHARACTERISTIC_UUID
            ).get();
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    @Override
    public List<byte[]> transceive(List<byte[]> commands) throws IOException {
        List<byte[]> result = new ArrayList<>(commands.size());

        for (byte[] cmd: commands) {
            result.add(transceive(cmd));
        }

        return result;
    }

}
