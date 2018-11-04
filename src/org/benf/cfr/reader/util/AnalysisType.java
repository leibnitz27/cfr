package org.benf.cfr.reader.util;

public enum AnalysisType {
    JAR("jar"),
    WAR("war"),
    CLASS("class");

    private final String suffix;

    public String getSuffix() {
        return suffix;
    }

    AnalysisType(String suffix) {
        this.suffix = suffix;
    }
}
