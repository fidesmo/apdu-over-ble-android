package com.fidesmo.ble.client.apdu;

import com.fidesmo.ble.client.protocol.PacketFragmenter;

public interface CustomFragmentationProtocol {
    PacketFragmenter fragmenter(int var1, byte[] var2);

    CustomPacketDefragmenter deframenter();
}
