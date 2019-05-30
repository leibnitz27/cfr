package org.benf.cfr.reader.state;

import org.benf.cfr.reader.api.ClassFileSource;
import org.benf.cfr.reader.apiunreleased.ClassFileSource2;
import org.benf.cfr.reader.apiunreleased.JarContent;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

/*
 * Compatibility for old class file source.
 *
 * I guess I picked the wrong day to commit to an API.
 */
public class ClassFileSourceWrapper implements ClassFileSource2 {
    private final ClassFileSource classFileSource;

    public ClassFileSourceWrapper(ClassFileSource classFileSource) {
        this.classFileSource = classFileSource;
    }

    @Override
    public JarContent addJarContent(String jarPath) {
        return new JarContentImpl(
            classFileSource.addJar(jarPath),
                Collections.<String, String>emptyMap());
    }

    @Override
    public void informAnalysisRelativePathDetail(String usePath, String classFilePath) {
        classFileSource.informAnalysisRelativePathDetail(usePath, classFilePath);
    }

    @Override
    public Collection<String> addJar(String jarPath) {
        return classFileSource.addJar(jarPath);
    }

    @Override
    public String getPossiblyRenamedPath(String path) {
        return classFileSource.getPossiblyRenamedPath(path);
    }

    @Override
    public Pair<byte[], String> getClassFileContent(String path) throws IOException {
        return classFileSource.getClassFileContent(path);
    }
}
