package org.benf.cfr.reader.state;

import org.benf.cfr.reader.apiunreleased.JarContent;

import java.util.Collection;
import java.util.Map;

public class JarContentImpl implements JarContent {
    private final Collection<String> classFiles;
    private final Map<String, String> manifestEntries;

    JarContentImpl(Collection<String> classFiles, Map<String, String> manifestEntries) {
        this.classFiles = classFiles;
        this.manifestEntries = manifestEntries;
    }

    @Override
    public Collection<String> getClassFiles() {
        return classFiles;
    }

    @Override
    public Map<String, String> getManifestEntries() {
        return manifestEntries;
    }
}
