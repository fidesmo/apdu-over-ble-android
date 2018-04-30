package com.fidesmo.ble.client;

import android.Manifest;
import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import com.fidesmo.ble.R;
import com.fidesmo.ble.client.apdu.CardInfoClient;
import com.fidesmo.ble.client.models.CardInfo;
import com.fidesmo.ble.client.models.CardOperation;
import nordpol.IsoCard;
import nordpol.android.AndroidCard;
import nordpol.android.OnDiscoveredTagListener;
import nordpol.android.TagDispatcher;

import java.io.IOException;
import java.util.LinkedList;

import static com.fidesmo.ble.client.BleUtils.byteArrayToString;

@TargetApi(23)
public class MainActivity extends AppCompatActivity implements OnDiscoveredTagListener, BleDeviceListener, LogsConsumer {
    private String TAG = getClass().getSimpleName();

    final private int REQUEST_CODE_SCAN     = 123;
    final private int REQUEST_CODE_ADVERT   = 124;

    private BleDeviceScanner deviceScanner =
            BleDeviceScanner.singleServiceScanner(this, BlePeripheralService.SERVICE_WITHOUT_ENCRYPTION, this, this);

    private TagDispatcher nfcTagDispatcher;

    private IsoCard nfcCard;

    private LinkedList<CardOperation> pendingOperations = new LinkedList<>();

    private BroadcastReceiver apduReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final byte[] data = intent.getByteArrayExtra("apdu");
            final long requestId = intent.getLongExtra("id", -1L);

            pendingOperations.offerLast(new CardOperation(requestId, data));

            processPendingCardOperations();
        }
    };

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Use this check to determine whether BLE is supported on the device. Then you can selectively disable BLE-related features.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "BLE is not supported on this device", Toast.LENGTH_SHORT).show();
            finish();
        } else {
            LocalBroadcastManager broadcastManager = LocalBroadcastManager.getInstance(this);

            IntentFilter logFilter = new IntentFilter(BlePeripheralService.LOG);
            IntentFilter apduFilter = new IntentFilter(BlePeripheralService.BLE_APDU);
            IntentFilter conversationFinishFilter = new IntentFilter(BlePeripheralService.CONVERSATION_FINISHED);

            broadcastManager.registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    final String message = intent.getExtras().getString("data");
                    log("SERVICE", message);
                }
            }, logFilter);

            broadcastManager.registerReceiver(apduReceiver, apduFilter);

            broadcastManager.registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    log("Conversation finished");
                    pendingOperations.clear();
                }
            }, conversationFinishFilter);

            askForBtDevicePermissionsAndFireAction(REQUEST_CODE_ADVERT);
        }

        nfcTagDispatcher = TagDispatcher.get(this, this, false, false, false, true, false, true);
    }


    @Override
    public void tagDiscovered(Tag tag) {
        try {
            nfcCard = AndroidCard.get(tag);

            if (pendingOperations.isEmpty()) {
                log("NFC Card attached. Awaiting for connection");
            } else {
                log("NFC Card attached. Processing pending operations");
            }

            processPendingCardOperations();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        deviceScanner.stopScan();
    }

    @Override
    protected void onPause() {
        super.onPause();
        deviceScanner.stopScan();
        nfcTagDispatcher.disableExclusiveNfc();
    }

    @Override
    protected void onResume() {
        super.onResume();
        nfcTagDispatcher.enableExclusiveNfc();
    }

    @Override
    public void onNewIntent(Intent intent) {
        Tag tagFromIntent = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        nfcTagDispatcher.interceptIntent(intent);
    }

    public void onDiscoveryClicked(View v) {
        if(!deviceScanner.isActive()) {
            clearLog();
            log("Discovery clicked");
            askForBtDevicePermissionsAndFireAction(REQUEST_CODE_SCAN);
        } else {
            deviceScanner.stopScan();
        }
    }

    private void askForBtDevicePermissionsAndFireAction(int requestCode) {
        int hasWriteBtPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN);
        int hasWriteLocPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION);

        if (hasWriteBtPermission != PackageManager.PERMISSION_GRANTED || hasWriteLocPermission != PackageManager.PERMISSION_GRANTED ) {

            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.BLUETOOTH_ADMIN)) {
                Toast.makeText(MainActivity.this, "App needs bluetooth to work", Toast.LENGTH_SHORT).show();
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[] {Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.ACCESS_COARSE_LOCATION},
                        requestCode);

            }

            return ;
        }

        enableBluetoothAndAction(requestCode);
    }

    @Override
    public void deviceDiscovered(BluetoothDevice bluetoothDevice) {
        log("BLE device discovered. Obtaining card information");

        boolean bond = bluetoothDevice.createBond();

        log("Creating bond: " + bond);

        CardInfoClient client = new CardInfoClient(new BleCard(this, bluetoothDevice, this));

        try {
            CardInfo cardInfo = client.getCardInfo();

            log("Card info: IIN: " + Utils.encodeHex(cardInfo.getIin()) +
                    ", platform: " + cardInfo.getCapabilities().getPlatformVersion() +
                    ", cin: " + Utils.encodeHex((cardInfo.getCin()))
            );

        } catch (Exception e) {
            log("Failed to get remote card information");
            e.printStackTrace();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE_SCAN:
            case REQUEST_CODE_ADVERT:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED &&
                    grantResults[1] == PackageManager.PERMISSION_GRANTED &&
                    grantResults[2] == PackageManager.PERMISSION_GRANTED) {

                    enableBluetoothAndAction(requestCode);
                } else {
                    Toast.makeText(MainActivity.this, "Permission Denied", Toast.LENGTH_SHORT).show();
                }

                break;

            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private void enableBluetoothAndAction(int requestCode) {
        BluetoothManager btManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        BluetoothAdapter btAdapter = btManager.getAdapter();

        if (btAdapter == null || !btAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, requestCode);
            return;
        }

        onActivityResult(requestCode, 0, null);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CODE_ADVERT:
                startAdvertise();
                break;
            case REQUEST_CODE_SCAN:
                startScan();
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void startScan() {
        deviceScanner.startDiscovery();
    }

    private void startAdvertise() {
        Intent intent = new Intent(this, BlePeripheralService.class);
        startService(intent);

        Intent localIntent = new Intent(BlePeripheralService.ACTION).putExtra("cmd", BlePeripheralService.CMD_LOGS);
        LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
    }

    private void processPendingCardOperations() {
        if ( nfcCard == null) {
            log("Please attach card to the phone, operations pending:" + pendingOperations.size());
            return;
        }

        CardOperation operation = pendingOperations.poll();

        while (operation != null && nfcCard != null) {
            try {

                if(!nfcCard.isConnected()) {
                    nfcCard.connect();
                }

                Log.i(TAG, "Trying to transcieve data to a card: " + byteArrayToString(operation.getRequest()));

                byte[] result = nfcCard.transceive(operation.getRequest());
                operation.setResponse(result);
                sendResponse(operation);

                operation = pendingOperations.poll();

            } catch (IOException e) {
                log("NFC card disconnected: " + e.getMessage());
                Log.w(TAG, e);
                pendingOperations.offerFirst(operation);
                nfcCard = null;
            }
        }
    }

    private void sendResponse(CardOperation op) {
        log("RESPONSE: ", op.toString());

        Intent localIntent = new Intent(BlePeripheralService.ACTION)
                .putExtra("cmd", BlePeripheralService.CMD_SE_RESPONSE)
                .putExtra("id", op.getId())
                .putExtra("apdu-response", op.getResponse());

        LocalBroadcastManager.getInstance(MainActivity.this).sendBroadcast(localIntent);
    }

    private void log(final String prefix, final String message) {
        runOnUiThread(new Runnable() { public void run() {
            TextView tv = (TextView) findViewById(R.id.outputView);
            tv.append(prefix + ": " + message + "\n");
            Log.i(TAG, message);
        }
        });
    }

    private void clearLog() {
        ((TextView)findViewById(R.id.outputView)).setText("");
    }

    public void log(String message) {
        log("ACTIVITY", message);
    }

}
