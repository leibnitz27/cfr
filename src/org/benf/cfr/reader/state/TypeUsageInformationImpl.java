package org.benf.cfr.reader.state;

import org.benf.cfr.reader.bytecode.analysis.types.InnerClassInfo;
import org.benf.cfr.reader.bytecode.analysis.types.JavaRefTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.MapFactory;
import org.benf.cfr.reader.util.SetFactory;

import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 06/11/2013
 * Time: 08:04
 */
public class TypeUsageInformationImpl implements TypeUsageInformation {

    private final JavaRefTypeInstance analysisType;
    private final Set<JavaRefTypeInstance> usedRefTypes = SetFactory.newOrderedSet();
    private final Map<JavaRefTypeInstance, String> displayName = MapFactory.newMap();
    private final Set<String> shortNames = SetFactory.newSet();

    public TypeUsageInformationImpl(JavaRefTypeInstance analysisType, Set<JavaRefTypeInstance> usedRefTypes) {
        this.analysisType = analysisType;
        initialiseFrom(usedRefTypes);
    }

    private String generateInnerClassShortName(JavaRefTypeInstance clazz) {
        InnerClassInfo innerClassInfo = clazz.getInnerClassHereInfo();
        String clazzRawName = clazz.getRawName();
        /* Local inner class.  We want the smallest postfix
         */
        JavaRefTypeInstance parent = innerClassInfo.getOuterClass();
        if (parent == null) return clazzRawName;

        if (clazz.getRawName().startsWith(analysisType.getRawName())) {
            return clazzRawName.substring(parent.getRawName().length() + 1);
        }
        /*
         * Foreign inner class.  Take the existing determined shortname, and replace $ with '.'.
         */
        return getName(parent) + "." + clazz.getRawShortName();
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


        for (JavaRefTypeInstance type : usedRefs) {
            addDisplayName(type);
        }
    }

    private String addDisplayName(JavaRefTypeInstance type) {
        String already = displayName.get(type);
        if (already != null) return already;

        String useName = null;
        if (type.getInnerClassHereInfo().isInnerClass()) {
            useName = generateInnerClassShortName(type);
        } else {
            String shortName = type.getRawShortName();
            useName = shortNames.add(shortName) ? shortName : type.getRawName();
        }
        displayName.put(type, useName);
        return useName;
    }

    @Override
    public Set<JavaRefTypeInstance> getUsedClassTypes() {
        return SetFactory.newOrderedSet(usedRefTypes);
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
