package org.benf.cfr.reader.state;

import org.benf.cfr.reader.apiunreleased.ClassFileSource2;
import org.benf.cfr.reader.apiunreleased.JarContent;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.benf.cfr.reader.util.AnalysisType;

import java.io.IOException;
import java.util.Collection;

public class ClassFileSourceChained implements ClassFileSource2 {
    private final Collection<ClassFileSource2> sources;

    public ClassFileSourceChained(Collection<ClassFileSource2> sources) {
        this.sources = sources;
    }

    @Override
    public JarContent addJarContent(String jarPath, AnalysisType analysisType) {
        for (ClassFileSource2 source : sources) {
            JarContent res = source.addJarContent(jarPath, analysisType);
            if (res != null) {
                return res;
            }
        }
        return null;
    }

    @Override
    public void informAnalysisRelativePathDetail(String usePath, String classFilePath) {
        for (ClassFileSource2 source : sources) {
            source.informAnalysisRelativePathDetail(usePath, classFilePath);
        }
    }

    @Override
    public Collection<String> addJar(String jarPath) {
        for (ClassFileSource2 source : sources) {
            Collection<String> res = source.addJar(jarPath);
            if (res != null) {
                return res;
            }
        }
        return null;
    }

    @Override
    public String getPossiblyRenamedPath(String path) {
        for (ClassFileSource2 source : sources) {
            String res = source.getPossiblyRenamedPath(path);
            if (res != null) {
                return res;
            }
        }
        return null;
    }

    @Override
    public Pair<byte[], String> getClassFileContent(String path) throws IOException {
        for (ClassFileSource2 source : sources) {
            Pair<byte[], String> res = source.getClassFileContent(path);
            if (res != null) {
                return res;
            }
        }
        return null;
    }
}
