package com.fidesmo.ble.client.models;

public class CardBatch {
    private final long issuer;
    private final int batchId;

    public CardBatch(long issuer, int batchId) {
        this.issuer = issuer;
        this.batchId = batchId;
    }

    public long getIssuer() {
        return issuer;
    }

    public int getBatchId() {
        return batchId;
    }

    @Override
    public String toString() {
        return "CardBatch{" +
                "issuer=" + issuer +
                ", batchId=" + batchId +
                '}';
    }
}
