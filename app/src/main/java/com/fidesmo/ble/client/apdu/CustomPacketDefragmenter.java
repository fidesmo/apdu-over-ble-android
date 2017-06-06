package com.fidesmo.ble.client.apdu;


public interface CustomPacketDefragmenter {
    void clear();

    void append(byte[] buffer);

    void add(byte[] buffer);

    boolean complete();

    boolean empty();

    byte[] getBuffer();
}