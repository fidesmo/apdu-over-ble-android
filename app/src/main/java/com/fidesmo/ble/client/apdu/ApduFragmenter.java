package com.fidesmo.ble.client.apdu;

public interface ApduFragmenter {

    byte[][] decode(byte[] encodedApdus);
    byte[] encode(byte[][] apdus);

}
