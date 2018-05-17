package com.fidesmo.ble.client;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class Utils {
    final private static char[] hexArray = "0123456789ABCDEF".toCharArray();

    public static byte[] decodeHex(String hexString) {
        if ((hexString.length() & 0x01) != 0) {
            throw new IllegalArgumentException("Odd number of characters.");
        }
        char[] hexChars = hexString.toUpperCase(Locale.ROOT).toCharArray();
        byte[] result = new byte[hexChars.length / 2];
        for (int i = 0; i < hexChars.length; i += 2) {
            result[i / 2] = (byte) (Arrays.binarySearch(hexArray, hexChars[i]) * 16 +
                    Arrays.binarySearch(hexArray, hexChars[i + 1]));
        }
        return result;
    }

    public static String encodeHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int i = 0; i < bytes.length; i++ ) {
            int v = bytes[i] & 0xFF;
            hexChars[i * 2] = hexArray[v >>> 4];
            hexChars[i * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    public static byte[] toApduSequence(List<byte[]> commands) {
        final int apduLenHeader = 2;
        final int apduNumberHeader = 2;

        int totalLen = apduNumberHeader;
        for (byte[] c: commands) {
            totalLen += c.length + apduLenHeader; // apdu len header
        }

        ByteBuffer result = ByteBuffer.allocate(totalLen);
        result.putShort((short)commands.size());

        for (byte[] c: commands) {
            result.putShort((short)c.length);
            result.put(c);
        }

        return result.array();
    }

    public static List<byte[]> fromApduSequence(byte[] responses) {
        ByteBuffer bf = ByteBuffer.wrap(responses);

        int count = bf.getShort();

        if (count > 100) {
            throw new IllegalArgumentException("Number of APDUs cannot exceed 100");
        }

        List<byte[]> result = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            int len = bf.getShort();

            if (len > 255) {
                throw new IllegalArgumentException("APDU cannot be bigger than 255");
            }

            byte[] apdu = new byte[len];
            bf.get(apdu);

            result.add(apdu);
        }

        return result;
    }
}
