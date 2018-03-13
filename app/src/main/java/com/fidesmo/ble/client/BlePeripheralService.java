package com.fidesmo.ble.client;

import android.annotation.TargetApi;
import android.app.Service;
import android.bluetooth.*;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;
import com.fidesmo.ble.client.protocol.FragmentationProtocol;
import com.fidesmo.ble.client.protocol.PacketDefragmenter;
import com.fidesmo.ble.client.protocol.PacketFragmenter;
import com.fidesmo.ble.client.protocol.SimplePacketFragmenter;

import java.util.LinkedList;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;

import static com.fidesmo.ble.client.BleCard.APDU_CONVERSATION_FINISHED_CHARACTERISTIC_UUID;
import static com.fidesmo.ble.client.BleUtils.*;
import static android.bluetooth.BluetoothProfile.GATT_SERVER;

/**
 * Implementation of BLE server that receives APDUs over BLE and passes them to the MainActivity using intent.
 * Responses from a card then received back and passed to the client.
 */
@TargetApi(21)
public class BlePeripheralService extends Service {

    public static final String ACTION   = "com.fidesmo.ble.client.BlePeripheralService.ACTION";
    public static final String LOG      = "com.fidesmo.ble.client.BlePeripheralService.LOG";
    public static final String BLE_APDU = "com.fidesmo.ble.client.BlePeripheralService.BLE_APDU";
    public static final String CONVERSATION_FINISHED = "com.fidesmo.ble.client.BlePeripheralService.CONVERSATION_FINISHED";
    // Client Characteristic Configuration Descriptor (CCCD): https://www.bluetooth.com/specifications/gatt/descriptors
    public static final String CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";

    public static final String CMD_LOGS = "LOGS";
    public static final String CMD_SE_RESPONSE = "SE_RESPONSE";
    public static final String CMD_STOP = "STOP";
    public static final String NFC_RESPONSE = "NFC_RESPONSE";

    public static final int MAX_LOG_BUFFER = 200;
    public static final int MAX_MEMORY = 512;

    private final IBinder binder = new LocalBinder();

    private BluetoothManager btManager;
    private BluetoothAdapter btAdapter;
    private BluetoothGattServerCallback gattServerCallback;
    private BluetoothGattServer gattServer;

    private AdvertiseSettings advertSettings;
    private AdvertiseCallback advertCallback;

    private BluetoothGattCharacteristic readNotifyCharacteristic;

    private LinkedList<String> messagesList = new LinkedList<>();

    private FragmentationProtocol fragmentationProtocol = SimplePacketFragmenter.factory();
    private PacketFragmenter currentResponsePacket;
    private PacketDefragmenter currentPacketBuilder;
    private int mtu = 512;

    private AtomicLong requestId = new AtomicLong(0);

    public BlePeripheralService() {}

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return super.onUnbind(intent);
    }

    @Override
    public void onCreate() {
        close();

        btManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        btAdapter = btManager.getAdapter();
        startServer();
        startAdvertisement();

        IntentFilter filter = new IntentFilter(ACTION);
        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver, filter);
    }

    public void onDestroy() {
        close();
    }

    public void startAdvertisement() {
        log("Starting to advertise device");

        // TODO: fails with NoSuchMethod on devices with API <= 19
        if (!getPackageManager().hasSystemFeature("android.hardware.bluetooth_le")) {
            log("BLE is not supported on this device");
            return;
        }

        if (!btAdapter.isMultipleAdvertisementSupported()) {
            log("No Advertising Support");
            return;
        }

        advertSettings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .setConnectable(true)
                .setTimeout(0)
                .build();

        ParcelUuid pUuid = new ParcelUuid(BleCard.APDU_SERVICE_UUID);

        AdvertiseData data = new AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .addServiceUuid(pUuid)
                .build();

        final BluetoothLeAdvertiser advertiser = btAdapter.getBluetoothLeAdvertiser();

        if (advertiser == null) {
            log("Advertising is not supported on this device");
            Toast.makeText(this, "Advertising is not supported", Toast.LENGTH_LONG).show();
            return ;
        }

        if (advertCallback == null) {
            advertCallback = new AdvertiseCallback() {
                @Override
                public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                    super.onStartSuccess(settingsInEffect);
                    log("Advertising started: " + settingsInEffect + ", device: " + btAdapter.getName() + ", addr: " + btAdapter.getAddress());
                }

                @Override
                public void onStartFailure(int errorCode) {
                    super.onStartFailure(errorCode);

                    String errCause = "unknown";

                    switch(errorCode) {
                        case AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED:
                            errCause = "Already started";
                            break;
                        case AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE:
                            errCause = "Data too large";
                            break;
                        case AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED:
                            errCause = "Feature unsupported";
                            break;
                        case AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR:
                            errCause = "Internal error";
                            break;
                        case AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS:
                            errCause = "Too many advertisers";
                            break;

                    }

                    log("Advertising onStartFailure: " + errCause + "(" + errorCode + ")");

                    close();
                }
            };
        }


        advertiser.startAdvertising(advertSettings, data, advertCallback);
    }

    public void startServer() {
        gattServerCallback = new BluetoothGattServerCallback() {
            @Override
            public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
                log("onConnectionStateChange: " + BleUtils.getStateDescription(newState));

                if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    // Can cause calling finishConversation() twice on Conversation finished command and on connection close.
                    // But as we only cleaning up the list – it won't harm, but with it in case of an error – we still sending cleanup commands.
                    finishConversation();
                }
            }

            @Override
            public void onServiceAdded(int status, BluetoothGattService service) {
                log("serviceAdded: " + service.getUuid() + ", adding characteristics");
            }

            @Override
            public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
                super.onCharacteristicReadRequest(device, requestId, offset, characteristic);

                if (characteristic.getUuid().equals(BleCard.APDU_MAX_MEMORY_FOR_APDU_PROCESSING)) {
                    log("Returning max memory for APDU processing value. Characteristic: " + characteristic.getUuid());
                    byte[] buf = new byte[4];
                    BleUtils.packInt4(MAX_MEMORY, buf, 0);
                    gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, buf);
                    return ;
                }


                if (!characteristic.getUuid().equals(BleCard.APDU_READ_CHARACTERISTIC_UUID)) {
                    gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null);
                    log("Unsupported characteristics read: " + characteristic.getUuid());
                    return ;
                }

                if (currentResponsePacket == null) {
                    log("No answer ready yet");
                    gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null);
                    return;
                }

                if (currentResponsePacket.hasMoreData()) {
                    gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, currentResponsePacket.nextFragment());
                } else {
                    gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, new byte[]{0});
                }


                if (!currentResponsePacket.hasMoreData()) {
                    currentResponsePacket = null;
                }
            }

            @Override
            public void onCharacteristicWriteRequest(final BluetoothDevice device, final int requestId,
                                                     BluetoothGattCharacteristic characteristic,
                                                     boolean preparedWrite, boolean responseNeeded,
                                                     final int offset,
                                                     byte[] value) {
                log("onCharacteristicWriteRequest(" + requestId + "): " + characteristic.getUuid() + ", value: " +
                       BleUtils.byteArrayToString(value) +
                    ", flags: prepared=" + preparedWrite + ", respNeeded=" + responseNeeded + ", offset: " + offset
                );

                if (responseNeeded) {
                    BleUtils.retryCall(new Callable<Boolean>() {
                        @Override
                        public Boolean call() throws Exception {
                            return gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, new byte[]{0});
                        }
                    });
                }

                if (offset != 0) {
                    log("Offset is not zero: " + offset);
                    return;
                }

                if(APDU_CONVERSATION_FINISHED_CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
                    finishConversation();
                    return;
                }


                if (currentPacketBuilder == null) {
                    log("Starting APDU request session");
                    currentPacketBuilder = fragmentationProtocol.deframenter();
                }

                currentPacketBuilder.appendPacket(value);

                if (currentPacketBuilder.isCompleted()) {
                    log("Packet received");
                    sendBleAPDUToActivity(currentPacketBuilder.fullData());
                    currentPacketBuilder = null;
                }
            }

            @Override
            public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattDescriptor descriptor) {
                super.onDescriptorReadRequest(device, requestId, offset, descriptor);
                log("onDescriptorReadRequest: " + descriptor.getUuid() + " char: " + descriptor.getCharacteristic().getUuid());
                gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, descriptor.getValue());
            }

            @Override
            public void onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor,
                                                 boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
                super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value);
                log("onDescriptorWriteRequest: " + descriptor.getUuid() + " char: " + descriptor.getCharacteristic().getUuid());
                gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
            }

            @Override
            public void onExecuteWrite(BluetoothDevice device, int requestId, boolean execute) {
                log("onExecuteWrite(" + requestId + "), execute: " + execute);
                gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null);
            }

            @Override
            public void onNotificationSent(BluetoothDevice device, int status) {
                log("onNotificationSent: " + device.getAddress() + ", status: " + status);
            }

            @Override
            public void onMtuChanged(BluetoothDevice device, int mtu) {
                log("onMtuChanged: " + mtu);
            }
        };

        log("Starting Gatt Server");

        if(gattServer == null) {

            gattServer = btManager.openGattServer(this, gattServerCallback);

            BluetoothGattService service = new BluetoothGattService(BleCard.APDU_SERVICE_UUID,
                                                                        BluetoothGattService.SERVICE_TYPE_PRIMARY);

            readNotifyCharacteristic =
                    new BluetoothGattCharacteristic(BleCard.APDU_RESPONSE_READY_NOTIFY_CHARACTERISTIC_UUID,
                            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                            BluetoothGattCharacteristic.PERMISSION_READ);

            // Notification descriptor is needed to be added to "readNotifyCharacteristic"
            BluetoothGattDescriptor gD = new BluetoothGattDescriptor(UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG),
                    BluetoothGattDescriptor.PERMISSION_WRITE | BluetoothGattDescriptor.PERMISSION_READ);
            gD.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            readNotifyCharacteristic.addDescriptor(gD);

            BluetoothGattCharacteristic readCharacteristic =
                    new BluetoothGattCharacteristic(BleCard.APDU_READ_CHARACTERISTIC_UUID,
                            BluetoothGattCharacteristic.PROPERTY_READ,
                            BluetoothGattCharacteristic.PERMISSION_READ);

            BluetoothGattCharacteristic writeCharacteristic =
                    new BluetoothGattCharacteristic(BleCard.APDU_WRITE_CHARACTERISTIC_UUID,
                            BluetoothGattCharacteristic.PROPERTY_WRITE,
                            BluetoothGattCharacteristic.PERMISSION_WRITE);

            BluetoothGattCharacteristic maxMemoryCharacteristic =
                    new BluetoothGattCharacteristic(BleCard.APDU_MAX_MEMORY_FOR_APDU_PROCESSING,
                            BluetoothGattCharacteristic.PROPERTY_READ,
                            BluetoothGattCharacteristic.PERMISSION_READ);

            BluetoothGattCharacteristic finishConversationCharacteristic =
                    new BluetoothGattCharacteristic(APDU_CONVERSATION_FINISHED_CHARACTERISTIC_UUID,
                            BluetoothGattCharacteristic.PROPERTY_WRITE,
                            BluetoothGattCharacteristic.PERMISSION_WRITE);

            service.addCharacteristic(readNotifyCharacteristic);
            service.addCharacteristic(maxMemoryCharacteristic);
            service.addCharacteristic(readCharacteristic);
            service.addCharacteristic(writeCharacteristic);
            service.addCharacteristic(finishConversationCharacteristic);

            boolean result = gattServer.addService(service);
            log("Added custom service: " + result);

            for(BluetoothGattService s : gattServer.getServices()) {
                log("Registered services: " + s.getUuid());
            }
        }
    }

    private void finishConversation() {
        Intent intent = new Intent(BlePeripheralService.CONVERSATION_FINISHED);
        LocalBroadcastManager.getInstance(BlePeripheralService.this).sendBroadcast(intent);
    }

    private void log(String s) {
        Log.i("BleService", s);
        Intent intent = new Intent(BlePeripheralService.LOG).putExtra("data", s);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);

        messagesList.add(s);

        if (messagesList.size() > MAX_LOG_BUFFER) {
            messagesList.removeFirst();
        }
    }

    private void sendBleAPDUToActivity(byte[] data) {
        Intent intent = new Intent(BlePeripheralService.BLE_APDU)
                                .putExtra("apdu", data)
                                .putExtra("id", requestId.get());

        log("APDU request session ended. Sending to a card. RequestId: " + requestId.get());

        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void passCardResponse(byte[] response) {
        currentResponsePacket = fragmentationProtocol.fragmenter(mtu, response);
        notifyAllDevices("OK");
        log("Current Request Id: " + requestId.incrementAndGet());
    }

    private void notifyAllDevices(String notification) {
        readNotifyCharacteristic.setValue(notification);

        log("Notifying about the result");

        for (BluetoothDevice device: btManager.getConnectedDevices(GATT_SERVER)) {
            final BluetoothDevice localDevice = device;
            log("Notifying device: " + localDevice.getAddress());

            BleUtils.retryCall(new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    return gattServer.notifyCharacteristicChanged(localDevice, readNotifyCharacteristic, false);
                }
            });

            break;
        }
    }

    public void close() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver);

        if (advertCallback != null) {
            btAdapter.getBluetoothLeAdvertiser().stopAdvertising(advertCallback);
            log("Advertising stopped");
            advertCallback = null;
        }

        if (gattServer != null) {
            gattServer.close();
        }
    }

    private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch(intent.getExtras().getString("cmd")) {
                case CMD_LOGS:
                    StringBuilder sb = new StringBuilder();

                    for (String record: messagesList) {
                        sb.append(record).append("\n");
                    }

                    Intent localIntent = new Intent(BlePeripheralService.LOG).putExtra("data", sb.toString());

                    LocalBroadcastManager.getInstance(BlePeripheralService.this)
                                         .sendBroadcast(localIntent);

                    break;
                case CMD_SE_RESPONSE:
                    byte[] response = intent.getByteArrayExtra("apdu-response");
                    long requestId = intent.getLongExtra("id", -1);
                    final long currentId = BlePeripheralService.this.requestId.get();

                    if (currentId == requestId) {
                        log("Card responded (" + requestId + ") " + byteArrayToString(response));
                        passCardResponse(response);
                    } else {
                        log("Received request: " + requestId + ", but current id is: " + requestId);
                    }

                    break;
                case CMD_STOP:
                    log("Stop command received");
                    stopSelf();
                    break;

                case NFC_RESPONSE:
                    log("responding with... ");
                    break;
            }
        }
    };

    public class LocalBinder extends Binder {}
}
