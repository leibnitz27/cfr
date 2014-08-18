package org.benf.cfr.reader.state;

import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.benf.cfr.reader.bytecode.analysis.types.InnerClassInfo;
import org.benf.cfr.reader.bytecode.analysis.types.JavaRefTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.util.*;
import org.benf.cfr.reader.util.output.CommaHelp;

import java.util.*;

public class TypeUsageInformationImpl implements TypeUsageInformation {

    private final JavaRefTypeInstance analysisType;
    private final Set<JavaRefTypeInstance> usedRefTypes = SetFactory.newOrderedSet();
    private final Set<JavaRefTypeInstance> usedLocalInnerTypes = SetFactory.newOrderedSet();
    private final Map<JavaRefTypeInstance, String> displayName = MapFactory.newMap();
    private final Set<String> shortNames = SetFactory.newSet();

    public TypeUsageInformationImpl(JavaRefTypeInstance analysisType, Set<JavaRefTypeInstance> usedRefTypes) {
        this.analysisType = analysisType;
        initialiseFrom(usedRefTypes);
    }

    @Override
    public String generateInnerClassShortName(JavaRefTypeInstance clazz) {
        return TypeUsageUtils.generateInnerClassShortName(clazz, analysisType);
    }

    private void initialiseFrom(Set<JavaRefTypeInstance> usedRefTypes) {
        List<JavaRefTypeInstance> usedRefs = ListFactory.newList(usedRefTypes);
        Collections.sort(usedRefs, new Comparator<JavaRefTypeInstance>() {
            @Override
            public int compare(JavaRefTypeInstance a, JavaRefTypeInstance b) {
                return a.getRawName().compareTo(b.getRawName());
            }
        });
        this.usedRefTypes.addAll(usedRefs);

        Pair<List<JavaRefTypeInstance>, List<JavaRefTypeInstance>> types = Functional.partition(usedRefs, new Predicate<JavaRefTypeInstance>() {
            @Override
            public boolean test(JavaRefTypeInstance in) {
                return in.getInnerClassHereInfo().isTransitiveInnerClassOf(analysisType);
            }
        });
        addDisplayNames(types.getFirst());
        this.usedLocalInnerTypes.addAll(types.getFirst());
        addDisplayNames(types.getSecond());
    }

    private void addDisplayNames(Collection<JavaRefTypeInstance> types) {
        for (JavaRefTypeInstance type : types) {
            addDisplayName(type);
        }
    }

    private String addDisplayName(final JavaRefTypeInstance type) {
        String already = displayName.get(type);
        if (already != null) return already;

        String useName = null;
        InnerClassInfo innerClassInfo = type.getInnerClassHereInfo();
        if (innerClassInfo.isInnerClass()) {
            useName = generateInnerClassShortName(type);
            shortNames.add(useName);
        } else {
            String shortName = type.getRawShortName();
            useName = shortNames.add(shortName) ? shortName : type.getRawName();
        }
        displayName.put(type, useName);
        return useName;
    }

    @Override
    public Set<JavaRefTypeInstance> getUsedClassTypes() {
        return usedRefTypes;
    }

    @Override
    public Set<JavaRefTypeInstance> getUsedInnerClassTypes() {
        return usedLocalInnerTypes;
    }

    @Override
    public String getName(JavaTypeInstance type) {
        String res = displayName.get(type);
        if (res == null) {
            // This should not happen.
            return type.getRawName();
//            throw new IllegalStateException();
        }
        return res;
    }
}
