package org.benf.cfr.reader.entities;

import org.benf.cfr.reader.bytecode.analysis.types.ClassNameUtils;
import org.benf.cfr.reader.bytecode.analysis.types.InnerClassInfo;
import org.benf.cfr.reader.bytecode.analysis.types.JavaRefTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.MapFactory;
import org.benf.cfr.reader.util.SetFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 02/04/2013
 * Time: 17:54
 */
public class ClassCache {

    private final Map<String, String> longNameToShortName = MapFactory.newMap();
    private final Map<String, String> shortNameToLongName = MapFactory.newMap();
    private final Map<String, JavaRefTypeInstance> refClassTypeCache = MapFactory.newMap();
    private final Set<String> importableClasses = SetFactory.newSet();

    private void markClassNameUsed(JavaRefTypeInstance typeInstance) {
        String className = typeInstance.getRawName();
        int idxlast = className.lastIndexOf('.');
        String partname = idxlast == -1 ? className : className.substring(idxlast + 1);
        InnerClassInfo innerClassInfo = typeInstance.getInnerClassHereInfo();
        if (!shortNameToLongName.containsKey(partname)) {
            shortNameToLongName.put(partname, className);
            longNameToShortName.put(className, partname);
            if (!innerClassInfo.isInnerClass()) { // actually, should only veto inners of main class.
                importableClasses.add(className);
            }
        }
        if (innerClassInfo.isInnerClass()) {
            JavaRefTypeInstance outerClass = innerClassInfo.getOuterClass();
            if (outerClass != null) {
                String innerName = className.substring(outerClass.getRawName().length() + 1);
                longNameToShortName.put(className, innerName);
            }
        }
    }

    public String getDisplayableClassName(String className) {
        String res = longNameToShortName.get(className);
        if (res == null) return className;
        return res;
    }

    public JavaRefTypeInstance getRefClassFor(String rawClassName) {
        String name = ClassNameUtils.convertFromPath(rawClassName);
        JavaRefTypeInstance typeInstance = refClassTypeCache.get(name);
        if (typeInstance != null) return typeInstance;

        typeInstance = JavaRefTypeInstance.create(name, this);

        // Find an appropriate 'displayable' name.
        markClassNameUsed(typeInstance);

        refClassTypeCache.put(name, typeInstance);
        return typeInstance;
    }

    public List<String> getImports() {
        return ListFactory.newList(importableClasses);
    }
}
