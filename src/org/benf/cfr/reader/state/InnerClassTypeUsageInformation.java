package org.benf.cfr.reader.state;

import org.benf.cfr.reader.bytecode.analysis.types.JavaRefTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.util.collections.MapFactory;
import org.benf.cfr.reader.util.collections.SetFactory;
import org.benf.cfr.reader.util.output.IllegalIdentifierDump;

import java.util.Map;
import java.util.Set;

/**
 * Strips the outer class name off anything which preceeds this inner class.
 */
public class InnerClassTypeUsageInformation implements TypeUsageInformation {
    private final IllegalIdentifierDump iid;
    private final TypeUsageInformation delegate;
    private final JavaRefTypeInstance analysisInnerClass;
    private final Map<JavaRefTypeInstance, String> localTypeNames = MapFactory.newMap();
    private final Set<String> usedLocalTypeNames = SetFactory.newSet();
    private final Set<JavaRefTypeInstance> usedInnerClassTypes = SetFactory.newSet();

    public InnerClassTypeUsageInformation(TypeUsageInformation delegate, JavaRefTypeInstance analysisInnerClass) {
        this.delegate = delegate;
        this.analysisInnerClass = analysisInnerClass;
        this.iid = delegate.getIid();
        initializeFrom();
    }

    @Override
    public IllegalIdentifierDump getIid() {
        return iid;
    }

    @Override
    public JavaRefTypeInstance getAnalysisType() {
        return delegate.getAnalysisType();
    }

    private void initializeFrom() {
        Set<JavaRefTypeInstance> outerInners = delegate.getUsedInnerClassTypes();
        for (JavaRefTypeInstance outerInner : outerInners) {
            if (outerInner.getInnerClassHereInfo().isTransitiveInnerClassOf(analysisInnerClass)) {
                usedInnerClassTypes.add(outerInner);
                String name = TypeUsageUtils.generateInnerClassShortName(iid, outerInner, analysisInnerClass, false);
                if (!usedLocalTypeNames.contains(name)) {
                    localTypeNames.put(outerInner, name);
                    usedLocalTypeNames.add(name);
                }
            }
        }
    }

    @Override
    public Set<JavaRefTypeInstance> getUsedClassTypes() {
        return delegate.getUsedClassTypes();
    }

    @Override
    public Set<JavaRefTypeInstance> getUsedInnerClassTypes() {
        return usedInnerClassTypes;
    }

    @Override
    public String getName(JavaTypeInstance type) {
        //noinspection SuspiciousMethodCalls - if it fails it fails....
        String local = localTypeNames.get(type);
        if (local != null) return local;

        String res = delegate.getName(type);
        if (usedLocalTypeNames.contains(res)) {
            return type.getRawName();
        }
        return res;
    }


    @Override
    public String generateInnerClassShortName(JavaRefTypeInstance clazz) {
        return delegate.generateInnerClassShortName(clazz);
    }

    @Override
    public String generateOverriddenName(JavaRefTypeInstance clazz) {
        return delegate.generateOverriddenName(clazz);
    }

    @Override
    public Set<JavaRefTypeInstance> getShortenedClassTypes() {
        return delegate.getShortenedClassTypes();
    }
}
