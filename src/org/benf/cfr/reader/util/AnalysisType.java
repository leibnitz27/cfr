package org.benf.cfr.reader.util;

public enum AnalysisType {
    JAR("jar"),
    WAR("war"),
    CLASS("class");

    private final String suffix;

    private AnalysisType(String suffix) {
        this.suffix = suffix;
    }
}
