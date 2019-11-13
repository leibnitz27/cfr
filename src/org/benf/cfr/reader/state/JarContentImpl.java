package org.benf.cfr.reader.state;

import org.benf.cfr.reader.apiunreleased.JarContent;
import org.benf.cfr.reader.util.AnalysisType;

import java.util.Collection;
import java.util.Map;

public class JarContentImpl implements JarContent {
    private final Collection<String> classFiles;
    private final Map<String, String> manifestEntries;
    private final AnalysisType analysisType;

    JarContentImpl(Collection<String> classFiles, Map<String, String> manifestEntries, AnalysisType analysisType) {
        this.classFiles = classFiles;
        this.manifestEntries = manifestEntries;
        this.analysisType = analysisType;
    }

    @Override
    public Collection<String> getClassFiles() {
        return classFiles;
    }

    @Override
    public Map<String, String> getManifestEntries() {
        return manifestEntries;
    }

    @Override
    public AnalysisType getAnalysisType() {
        return analysisType;
    }
}
