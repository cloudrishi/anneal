package com.rish.anneal.core.model;

public enum JavaVersion {

    V8(8),
    V9(9),
    V11(11),
    V17(17),
    V21(21),
    V25(25);

    private final int version;

    JavaVersion(int version) {
        this.version = version;
    }

    public int getVersion() {
        return version;
    }

    public boolean isLts() {
        return this == V8 || this == V11 || this == V17 || this == V21 || this == V25;
    }

    public boolean isOlderThan(JavaVersion other) {
        return this.version < other.version;
    }

    public boolean isNewerThan(JavaVersion other) {
        return this.version > other.version;
    }

    public static JavaVersion fromInt(int version) {
        for (JavaVersion v : values()) {
            if (v.version == version) {
                return v;
            }
        }
        throw new IllegalArgumentException("Unsupported Java version: " + version);
    }

    @Override
    public String toString() {
        return "Java " + version;
    }
}
