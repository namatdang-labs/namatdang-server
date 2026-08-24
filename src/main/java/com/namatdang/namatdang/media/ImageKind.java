package com.namatdang.namatdang.media;

public enum ImageKind {
    STORE("stores"),
    DEAL("deals");

    private final String directory;

    ImageKind(String directory) {
        this.directory = directory;
    }

    public String directory() {
        return directory;
    }
}
