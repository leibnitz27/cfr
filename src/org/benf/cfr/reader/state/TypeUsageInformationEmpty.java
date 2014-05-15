package org.benf.cfr.reader.state;

import org.benf.cfr.reader.bytecode.analysis.types.JavaRefTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.util.SetFactory;

import java.util.Set;

public class TypeUsageInformationEmpty implements TypeUsageInformation {
    @Override
    public Set<JavaRefTypeInstance> getUsedClassTypes() {
        return SetFactory.newOrderedSet();
    }

    @Override
    public Set<JavaRefTypeInstance> getUsedInnerClassTypes() {
        return SetFactory.newOrderedSet();
    }

    @Override
    public String getName(JavaTypeInstance type) {
        return type.getRawName();
    }

    @Override
    public String generateInnerClassShortName(JavaRefTypeInstance clazz) {
        return clazz.getRawName();
    }
}
