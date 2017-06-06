package com.fidesmo.ble.client.apdu;

import android.util.Log;

import com.fidesmo.ble.client.BleUtils;
import com.fidesmo.ble.client.Utils;

/**
 * This class splits big byte buffer formed by a list of apdus
 * into a list of single apdus.
 * Spec: https://github.com/fidesmo/apdu-over-ble/blob/master/spec/apdu-service-spec.md
 *
 * Created by Angel Anton on 30/05/17.
 */

public class SimpleApduFragmenter implements ApduFragmenter {

    private int TOTAL_HEADER_SIZE = 2;
    private int HEADER_SIZE = 2;

    @Override
    public byte[][] decode(byte[] encodedApdus) {
        int totalNo = BleUtils.unpackInt2(encodedApdus, 0);
        byte[][] apdus = new byte[totalNo][];
        int acc = TOTAL_HEADER_SIZE;

        for (int i = 0; i < totalNo; i++) {
            int apduSize = BleUtils.unpackInt2(encodedApdus, acc);
            byte[] apdu = new byte[apduSize];
            acc += HEADER_SIZE;
            System.arraycopy(encodedApdus, acc, apdu, 0, apduSize);
            apdus[i] = apdu;
            acc += apduSize;
        }
        return apdus;
    }

    @Override
    public byte[] encode(byte[][] apdus) {
        byte[] buffer = new byte[2];
        BleUtils.packInt2(apdus.length, buffer, 0);
        int acc = TOTAL_HEADER_SIZE;

        for (int i = 0; i < apdus.length; i++) {
            byte[] apdu = apdus[i];
            buffer = ensureBuffer(buffer, buffer.length + HEADER_SIZE + apdu.length);
            BleUtils.packInt2(apdu.length, buffer, acc);
            acc += HEADER_SIZE;
            System.arraycopy(apdu, 0, buffer, acc, apdu.length);
            acc += apdu.length;
        }

        return buffer;
    }

    private byte[] ensureBuffer(byte[] buffer, int size) {
        if (buffer.length < size) {
            byte[] oldBuf = buffer;
            buffer = new byte[size]; // + 256?
            System.arraycopy(oldBuf, 0, buffer, 0, oldBuf.length);
        }
        return buffer;
    }
}
