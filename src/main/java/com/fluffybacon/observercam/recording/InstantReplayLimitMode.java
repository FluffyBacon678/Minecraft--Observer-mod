package com.fluffybacon.observercam.recording;

public enum InstantReplayLimitMode {
    TIME("time"),
    SIZE("size");

    private final String id;

    InstantReplayLimitMode(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public InstantReplayLimitMode next() {
        InstantReplayLimitMode[] modes = values();
        return modes[(ordinal() + 1) % modes.length];
    }
}
