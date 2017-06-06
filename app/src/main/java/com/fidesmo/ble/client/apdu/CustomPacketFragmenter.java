package com.fidesmo.ble.client.apdu;


import android.util.Log;

import com.fidesmo.ble.client.BleUtils;
import com.fidesmo.ble.client.protocol.PacketFragmenter;
import com.fidesmo.ble.client.protocol.SimplePacketFragmenter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CustomPacketFragmenter implements PacketFragmenter {
    public static final int HEADER_SIZE = 4;
    private final int maxLength;
    private final byte[] buffer;
    private final int totalPackets;
    private int sentPackets = 0;

    public CustomPacketFragmenter(int maxLength, byte[] buffer) {
        if(maxLength <= HEADER_SIZE) {
            throw new IllegalArgumentException("MTU size can not be less of equal to header size");
        } else {
            this.maxLength = maxLength;
            this.buffer = Arrays.copyOf(buffer, buffer.length);
            int packetMax = maxLength - HEADER_SIZE;
            int carriedLen = Math.max(buffer.length, 1);
            this.totalPackets = carriedLen / packetMax + (carriedLen % packetMax > 0?1:0);
        }
    }

    public byte[] nextFragment() {
        int available = this.maxLength - HEADER_SIZE;
        int offset = available * this.sentPackets;
        int len = Math.min(available, this.buffer.length - offset);
        byte[] chunk = new byte[len + HEADER_SIZE];
        BleUtils.packInt2(this.totalPackets, chunk, 0);
        BleUtils.packInt2(++this.sentPackets, chunk, HEADER_SIZE/2);
        System.arraycopy(this.buffer, offset, chunk, HEADER_SIZE, len);
        return chunk;
    }

    public boolean hasMoreData() {
        return this.sentPackets < this.totalPackets;
    }

    public static CustomFragmentationProtocol factory() {
        return new CustomPacketFragmenter.Factory();
    }

    private static class Factory implements CustomFragmentationProtocol {
        private Factory() {
        }

        public PacketFragmenter fragmenter(int mtu, byte[] buffer) {
            return new CustomPacketFragmenter(mtu, buffer);
        }

        public CustomPacketDefragmenter deframenter() {
            return new CustomPacketFragmenter.Builder();
        }
    }

    public static class Builder implements CustomPacketDefragmenter {
        private int totalNo;
        private int packetNo;

        List<byte[]> byteArray;

        private Builder() {
            byteArray = new ArrayList<>();
            totalNo = 0;
            packetNo = 0;
        }

        public void clear(){
            byteArray.clear();
            totalNo = 0;
            packetNo = 0;
        }

        public void append(byte[] array){
            totalNo = BleUtils.unpackInt2(array, 0);
            packetNo = BleUtils.unpackInt2(array, HEADER_SIZE/2);
            byte[] buffer = new byte[array.length - HEADER_SIZE];
            System.arraycopy(array, HEADER_SIZE, buffer, 0, buffer.length);
            byteArray.add(buffer);
        }

        public void add(byte[] array){
            byteArray.add(array);
        }

        public byte[] getBuffer(){
            byte[] retArray;
            int totalSize = 0;

            for(int i=0; i < byteArray.size();i++){
                totalSize = totalSize + byteArray.get(i).length;
            }

            int copuCounter = 0;
            if(totalSize > 0) {
                retArray = new byte[totalSize];
                for(int ii=0; ii < byteArray.size();ii++){
                    byte[] tmpArr = byteArray.get(ii);
                    System.arraycopy(tmpArr, 0, retArray,copuCounter,tmpArr.length);
                    copuCounter = copuCounter + tmpArr.length;
                }
            }else{
                retArray = new byte[]{};
            }
            return retArray;
        }

        public boolean complete() {
            return totalNo == packetNo;
        }

        public boolean empty() { return totalNo == 0 && packetNo == 0; }
    }
}
