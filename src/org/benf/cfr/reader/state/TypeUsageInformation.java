package org.benf.cfr.reader.state;

import org.benf.cfr.reader.bytecode.analysis.types.JavaRefTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.util.MapFactory;
import org.benf.cfr.reader.util.SetFactory;

import java.util.Map;
import java.util.Set;

public interface TypeUsageInformation {
    public Set<JavaRefTypeInstance> getShortenedClassTypes();

    public Set<JavaRefTypeInstance> getUsedClassTypes();

    public Set<JavaRefTypeInstance> getUsedInnerClassTypes();

    public String getName(JavaTypeInstance type);

    public String generateInnerClassShortName(JavaRefTypeInstance clazz);

    public String generateOverriddenName(JavaRefTypeInstance clazz);
}
