package org.benf.cfr.reader.state;

import org.benf.cfr.reader.bytecode.analysis.types.JavaRefTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.util.collections.SetFactory;
import org.benf.cfr.reader.util.output.IllegalIdentifierDump;

import java.util.Collections;
import java.util.Set;

public class TypeUsageInformationEmpty implements TypeUsageInformation {
    public static final TypeUsageInformation INSTANCE = new TypeUsageInformationEmpty();

    @Override
    public JavaRefTypeInstance getAnalysisType() {
        return null;
    }

    @Override
    public IllegalIdentifierDump getIid() {
        return null;
    }

    @Override
    public boolean isStaticImport(JavaTypeInstance clazz, String fixedName) {
        return false;
    }

    @Override
    public Set<DetectedStaticImport> getDetectedStaticImports() {
        return Collections.emptySet();
    }

    @Override
    public Set<JavaRefTypeInstance> getUsedClassTypes() {
        return SetFactory.newOrderedSet();
    }

    @Override
    public Set<JavaRefTypeInstance> getUsedInnerClassTypes() {
        return SetFactory.newOrderedSet();
    }

    @Override
    public Set<JavaRefTypeInstance> getShortenedClassTypes() {
        return SetFactory.newOrderedSet();
    }

    @Override
    public String getName(JavaTypeInstance type) {
        return type.getRawName();
    }

    @Override
    public String generateOverriddenName(JavaRefTypeInstance clazz) {
        return clazz.getRawName();
    }

    @Override
    public String generateInnerClassShortName(JavaRefTypeInstance clazz) {
        return clazz.getRawName();
    }
}
