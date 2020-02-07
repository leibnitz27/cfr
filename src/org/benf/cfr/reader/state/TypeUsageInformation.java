package org.benf.cfr.reader.state;

import org.benf.cfr.reader.bytecode.analysis.types.JavaRefTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.util.output.IllegalIdentifierDump;

import java.util.Set;

public interface TypeUsageInformation {
    JavaRefTypeInstance getAnalysisType();

    Set<JavaRefTypeInstance> getShortenedClassTypes();

    Set<JavaRefTypeInstance> getUsedClassTypes();

    Set<JavaRefTypeInstance> getUsedInnerClassTypes();

    String getName(JavaTypeInstance type);

    String generateInnerClassShortName(JavaRefTypeInstance clazz);

    String generateOverriddenName(JavaRefTypeInstance clazz);

    IllegalIdentifierDump getIid();

    boolean isStaticImport(JavaTypeInstance clazz, String fixedName);

    Set<DetectedStaticImport> getDetectedStaticImports();
}
