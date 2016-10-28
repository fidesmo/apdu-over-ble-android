package com.fidesmo.ble.client.models;

public class Capabilities {
    private final long platformVersion;
    private final Integer mifareType;
    private final Integer uidSize;
    private final Integer jcVersion;
    private final Integer osTypeVersion;
    private final Integer globalPlatformVersion;

    public Capabilities(long platformVersion, Integer mifareType, Integer uidSize, Integer jcVersion, Integer osTypeVersion, Integer globalPlatformVersion) {
        this.platformVersion = platformVersion;
        this.mifareType = mifareType;
        this.uidSize = uidSize;
        this.jcVersion = jcVersion;
        this.osTypeVersion = osTypeVersion;
        this.globalPlatformVersion = globalPlatformVersion;
    }

    public long getPlatformVersion() {
        return platformVersion;
    }

    public Integer getMifareType() {
        return mifareType;
    }

    public Integer getUidSize() {
        return uidSize;
    }

    public Integer getJcVersion() {
        return jcVersion;
    }

    public Integer getOsTypeVersion() {
        return osTypeVersion;
    }

    public Integer getGlobalPlatformVersion() {
        return globalPlatformVersion;
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("Capabilities{");
        sb.append("platformVersion=").append(platformVersion);
        sb.append(", mifareType=").append(mifareType);
        sb.append(", uidSize=").append(uidSize);
        sb.append(", jcVersion=").append(jcVersion);
        sb.append(", osTypeVersion=").append(osTypeVersion);
        sb.append(", globalPlatformVersion=").append(globalPlatformVersion);
        sb.append('}');
        return sb.toString();
    }
}
