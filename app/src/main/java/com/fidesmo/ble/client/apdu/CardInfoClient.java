package com.fidesmo.ble.client.apdu;

import com.fidesmo.ble.client.BleCard;
import com.fidesmo.ble.client.Utils;
import com.fidesmo.ble.client.models.Capabilities;
import com.fidesmo.ble.client.models.CardBatch;
import com.fidesmo.ble.client.models.CardInfo;


import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Formatter;

public class CardInfoClient {
    protected static byte[] selectIsd = Utils.decodeHex("00A4040000");
    private static final String PLATFORM_VERSION_AID = "A000000617020002000001";
    private static final String CARD_DATA_AID = "A000000617020002000002";

    public static final int BATCH_TLV_ID = 0x42;
    public static final int ISSUER_TLV_ID = 0x43;

    private final BleCard device;

    public CardInfoClient(BleCard device) {
        this.device = device;
    }

    /** Apdus that can be sent to the isd client
     */
    protected static byte[] getData(int tag) {
        return Utils.decodeHex(String.format("80CA%04X00", tag & 0xFFFF).toUpperCase());
    }

    protected static int tagIin = 0x42;
    protected static int tagCin = 0x45;
    protected static int tagFci = 0x6F;
    protected static int tagAid = 0x84;

    // card capabilities tags
    protected static final int tagPlatformVersion = 0x41;
    protected static final int tagMifareType = 0x42;
    protected static final int tagUidSize = 0x43;
    protected static final int tagJcVersion = 0x44;
    protected static final int tagOsTypeVersion = 0x45;


    public CardInfo getCardInfo() throws Exception {
        if(!device.isConnected()) {
            device.connect();
        }

        CardInfo cardInfo = transceiveCardInfo();

        device.close();

        return cardInfo;
    }


    /** Get a unique identifier for the card by quering IIN and CIN */
    private CardInfo transceiveCardInfo() throws Exception {
        byte[] aid = transceiveSelectIsd();
        byte[] iin = transceiveGetData(tagIin, "Query issuer identification number");
        byte[] cin = transceiveGetData(tagCin, "Query card image number");
        Capabilities capabilities = transceiveCapabilites();
        CardBatch batch = transceiveBatchInfo();
        return new CardInfo(iin, cin, aid, batch, capabilities);
    }

    private Capabilities transceiveCapabilites() throws Exception {
        String select = String.format("00A40400%02X%s00",
                PLATFORM_VERSION_AID.length()/2,
                PLATFORM_VERSION_AID).toUpperCase();
        int[] statusWords = {0x6A82, 0x9000};
        byte[] response = transceive(Utils.decodeHex(select), "Query platform version", statusWords);
        ByteBuffer buffer = ByteBuffer.wrap(response);

        long platformVersion = 0;
        Integer mifareType = null;
        Integer uidSize = null;
        Integer jcVersion = null;
        Integer osTypeVersion = null;
        Integer gpVersion = null;

        while (buffer.remaining() > 2) {
            int tag = getTlvTag(buffer);
            byte data[] = getTlvData(buffer);

            switch (tag) {
                case tagPlatformVersion:
                    platformVersion = parseLong(data, data.length);
                    break;
                case tagMifareType:
                    mifareType = new Long(parseLong(data, data.length)).intValue();
                    break;
                case tagUidSize:
                    uidSize = new Long(parseLong(data, data.length)).intValue();
                    break;
                case tagJcVersion:
                    jcVersion = new Long(parseLong(data, data.length)).intValue();
                    break;
                case tagOsTypeVersion:
                    osTypeVersion = new Long(parseLong(data, data.length)).intValue();
                    break;
                default:
                    throw new Exception("Unexpected tag during transceive platform");
            }
        }

        // perform a select ISD to get global platform version
        ByteBuffer outer = ByteBuffer.wrap(transceive(selectIsd, "Select isd"));
        if (getTlvTag(outer) == tagFci) {
            ByteBuffer inner = searchForTlvInLv(outer, 0xA5);
            if (inner != null) {
                inner = searchForTlvInLv(inner, 0x73);
                if (inner != null) {
                    inner = searchForTlvInLv(inner, 0x60);
                    if (inner != null) {
                        inner = searchForTlvInLv(inner, 0x06);
                        if (inner != null) {
                            int value = 0;
                            byte[] oid = getTlvData(inner);

                            // read global platform version from last 2 or 3 bytes
                            for (int i = 7; i < oid.length; i ++) {
                                value = (value << 8) + (oid[i] & 0xFF);
                            }
                            gpVersion = value;
                        }
                    }
                }
            }
        }

        return new Capabilities(platformVersion,
                mifareType,
                uidSize,
                jcVersion,
                osTypeVersion,
                gpVersion);
    }

    /** Query card data with getData command
     */
    private byte[] transceiveGetData(int tag, String msg) throws Exception {
        byte[] response = transceive(getData(tag), msg);
        ByteBuffer buffer = ByteBuffer.wrap(response);

        if (getTlvTag(buffer) != tag) {
            throw new Exception(String.format("Invalid IIN tlv tag 0x%4X", tag));
        }

        return getTlvData(buffer);
    }

    private byte[] transceiveSelectIsd() throws Exception {
        ByteBuffer buffer = ByteBuffer.wrap(transceive(selectIsd, "Select isd"));

        if (getTlvTag(buffer) == tagFci) {
            ByteBuffer inner = ByteBuffer.wrap(getTlvData(buffer));
            if (getTlvTag(inner) == tagAid) {
                return getTlvData(inner);
            } else {
                throw new Exception("No AID tag present in FCI");
            }
        } else {
            throw new Exception("No FCI tag present in select response");
        }
    }

    private CardBatch transceiveBatchInfo() throws Exception {
        String select = String.format("00A40400%02X%s00",
                CARD_DATA_AID.length()/2,
                CARD_DATA_AID).toUpperCase();
        byte[] response = transceive(Utils.decodeHex(select), "Query account id");
        ByteBuffer buffer = ByteBuffer.wrap(response);

        while (getTlvTag(buffer) != BATCH_TLV_ID) {
            getTlvData(buffer);
        }

        byte[] batchBytes = getTlvData(buffer);
        int batchId = (int) parseLong(batchBytes, batchBytes.length);

        buffer.rewind();

        while (getTlvTag(buffer) != ISSUER_TLV_ID) {
            getTlvData(buffer);
        }

        long issuerId = parseLong(getTlvData(buffer), 6);

        return new CardBatch(issuerId, batchId);
    }

    public static long parseLong(byte[] data, int positions) {
        DataInputStream is = new DataInputStream(new ByteArrayInputStream(data));
        long result = 0;

        if (data.length != positions) {
            throw new RuntimeException("Data must be " + positions + " bytes");
        }

        try {
            for (int i = positions - 1; i >= 0; i--) {
                result += ((long) is.readUnsignedByte() << (8 * i));
            }
        } catch (IOException ioex) {
            throw new RuntimeException(ioex);
        }

        return result;
    }


    protected byte[] transceive(byte[] command) throws IOException {
        return device.transceive(command);
    }

    /** Send command to SE and reads its response. The status word is checked against a list of
     * expected status words. This list might contain entire status words or only prefixes. If
     * an unexpected status word is encountered an exception containg the description is thrown.
     *
     * @param command APDU that should be sent
     * @param description Human readable description of the operation performed
     * @param statusWords List of accepted status words or status word prefixes
     * @return Apdu response received
     */
    protected byte[] transceive(byte[] command, String description, int[] statusWords) throws Exception {
        byte[] response = transceive(command);

        int status = statusWord(response);
        boolean success = false;

        for (int expected: statusWords) {
            success = (expected <= 0xFF && (status >> 8) == expected) || (expected == status);
            if (success) {
                break;
            }
        }

        if(!success) {
            Formatter formatter = new Formatter();
            formatter.format("%s failed with status word %04X, %d", description, status, status);
            throw new Exception(formatter.toString());
        }
        return response;
    }

    /** Transceive with 0x9000 as only acceptable status word
     * @param command APDU that should be sent
     * @param description Human readable description of the operation performed
     * @return APDU response received
     */
    private byte[] transceive(byte[] command, String description) throws Exception {
        int[] accepted = { 0x9000 };
        return transceive(command, description, accepted);
    }

    /** Assumes BER tag is preset at current buffer position and
     *  extracts value */
    private int getTlvTag(ByteBuffer buffer) {
        int firstTagByte =  buffer.get() & 0xFF;
        if ((firstTagByte & 0x1F) == 0x1F) {
            return (firstTagByte << 8) + (buffer.get() & 0xFF);
        } else {
            return firstTagByte;
        }
    }

    /** Assumes that BER length|value is present at current buffer
     * position and extracts value */
    private byte[] getTlvData(ByteBuffer buffer) {
        int length =  buffer.get() & 0x7F; // this only works for length <= 127
        byte[] target = new byte[length];
        buffer.get(target);
        return target;
    }

    /** Assumes that non-primitive BER LV is present at the current buffer
     *  position, and LV for given inner tag. */
    private ByteBuffer searchForTlvInLv(ByteBuffer istream, int innerTag) {
        ByteBuffer buffer = ByteBuffer.wrap(getTlvData(istream));

        while (buffer.remaining() > 0  && getTlvTag(buffer) != innerTag) {
            getTlvData(buffer);
        }

        if (buffer.remaining() == 0) {
            return null;
        } else {
            return buffer;
        }
    }

    /** Get status word from response.
     * @param response Response from which the status code is to be extracted
     * @return The status word as an integer
     */
    private int statusWord(byte[] response) {
        int r = (response[response.length - 1] & 0xFF) +
                ((response[response.length - 2] & 0xFF) << 8);
        return r;
    }
}