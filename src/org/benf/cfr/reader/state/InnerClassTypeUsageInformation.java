package org.benf.cfr.reader.state;

import org.benf.cfr.reader.bytecode.analysis.types.JavaRefTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.util.MapFactory;
import org.benf.cfr.reader.util.SetFactory;

import java.util.Map;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 04/03/2014
 * Time: 06:19
 * <p/>
 * Strips the outer class name off anything which preceeds this inner class.
 */
public class InnerClassTypeUsageInformation implements TypeUsageInformation {
    private final TypeUsageInformation delegate;
    private final JavaRefTypeInstance analysisInnerClass;
    private final Map<JavaRefTypeInstance, String> localTypeNames = MapFactory.newMap();
    private final Set<String> usedLocalTypeNames = SetFactory.newSet();
    private final Set<JavaRefTypeInstance> usedInnerClassTypes = SetFactory.newSet();

    public InnerClassTypeUsageInformation(TypeUsageInformation delegate, JavaRefTypeInstance analysisInnerClass) {
        this.delegate = delegate;
        this.analysisInnerClass = analysisInnerClass;
        initializeFrom();
    }

    private void initializeFrom() {
        Set<JavaRefTypeInstance> outerInners = delegate.getUsedInnerClassTypes();
        for (JavaRefTypeInstance outerInner : outerInners) {
            if (outerInner.getInnerClassHereInfo().isTransitiveInnerClassOf(analysisInnerClass)) {
                usedInnerClassTypes.add(outerInner);
                String name = TypeUsageUtils.generateInnerClassShortName(outerInner, analysisInnerClass);
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
}
