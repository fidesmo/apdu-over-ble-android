package com.fidesmo.ble.client.models;

import java.util.Arrays;

public class CardInfo {
    private final byte[] iin;
    private final byte[] cin;
    private final byte[] isdAid;
    private final CardBatch batch;
    private final Capabilities capabilities;

    public CardInfo(byte[] iin, byte[] cin, byte[] isdAid, CardBatch batch, Capabilities capabilities) {
        this.iin = iin;
        this.cin = cin;
        this.isdAid = isdAid;
        this.batch = batch;
        this.capabilities = capabilities;
    }

    public byte[] getIin() {
        return iin;
    }

    public byte[] getCin() {
        return cin;
    }

    public byte[] getIsdAid() {
        return isdAid;
    }

    public CardBatch getBatch() {
        return batch;
    }

    public Capabilities getCapabilities() {
        return capabilities;
    }

    @Override
    public String toString() {
        return "CardInfo{" +
                "iin=" + Arrays.toString(iin) +
                ", cin=" + Arrays.toString(cin) +
                ", isdAid=" + Arrays.toString(isdAid) +
                ", batch=" + batch +
                ", capabilities=" + capabilities +
                '}';
    }
}
