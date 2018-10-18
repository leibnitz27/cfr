package org.benf.cfr.reader.api;

import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;

import java.io.IOException;
import java.util.Collection;

public interface ClassFileSource {
    // Fixme - should classFileSource interface know about this?
    void informAnalysisRelativePathDetail(String usePath, String classFilePath);

    // If we're explicitly adding a jar
    Collection<String> addJar(String jarPath);

    String getPossiblyRenamedPath(String path);

    // Return file content, plus location it came from (to be fed back to informAnalysis...)
    Pair<byte[], String> getClassFileContent(final String path) throws IOException;
}
