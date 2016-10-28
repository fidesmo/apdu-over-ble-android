package com.fidesmo.ble.client.models;

import java.util.Arrays;

public class CardOperation {
    private final long id;
    private final byte[] request;
    private byte[] response;

    public CardOperation(long id, byte[] request) {
        this.id = id;
        this.request = request;
    }

    public long getId() {
        return id;
    }

    public byte[] getRequest() {
        return request;
    }

    public byte[] getResponse() {
        return response;
    }

    public void setResponse(byte[] response) {
        this.response = response;
    }

    @Override
    public String toString() {
        return "CardOperation{" +
                "id=" + id +
                ", request=" + Arrays.toString(request) +
                ", response=" + Arrays.toString(response) +
                '}';
    }
}
