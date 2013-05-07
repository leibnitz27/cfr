package org.benf.cfr.reader.entities;

import org.benf.cfr.reader.bytecode.analysis.types.*;
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

    /*
     * Set just after constructing first type.
     */
    private transient JavaRefTypeInstance analysisType;

    public ClassCache() {
        refClassTypeCache.put(TypeConstants.STRING.getRawName(), TypeConstants.STRING);
        refClassTypeCache.put(TypeConstants.ENUM.getRawName(), TypeConstants.ENUM);
    }

    private boolean importClass(JavaRefTypeInstance clazz) {
        InnerClassInfo innerClassInfo = clazz.getInnerClassHereInfo();
        if (clazz == analysisType) return false; // yes, reference equality.
        // TODO : We should only be importing public inner classes which are referenced directly.
        if (!innerClassInfo.isInnerClass()) return true;
        if (clazz.getRawName().startsWith(analysisType.getRawName())) return false;
        return true;
    }

    private String generateInnerClassShortName(JavaRefTypeInstance clazz) {
        InnerClassInfo innerClassInfo = clazz.getInnerClassHereInfo();
        String clazzRawName = clazz.getRawName();
        /* Local inner class.  We want the smallest postfix
         */
        if (clazz.getRawName().startsWith(analysisType.getRawName())) {
            JavaRefTypeInstance parent = innerClassInfo.getOuterClass();
            if (parent == null) return clazzRawName;
            return clazzRawName.substring(parent.getRawName().length() + 1);
        }
        /*
         * Foreign inner class.  Take the existing determined shortname, and replace $ with '.'.
         */

        return longNameToShortName.get(clazzRawName).replace('$', '.');
    }

    public void setAnalysisType(JavaRefTypeInstance analysisType) {
        this.analysisType = analysisType;
        importableClasses.remove(analysisType.getRawName());
    }

    public void markClassNameUsed(JavaRefTypeInstance typeInstance) {

        String className = typeInstance.getRawName();
        int idxlast = className.lastIndexOf('.');
        String partname = idxlast == -1 ? className : className.substring(idxlast + 1);
        if (!shortNameToLongName.containsKey(partname)) {
            shortNameToLongName.put(partname, className);
            longNameToShortName.put(className, partname);
            if (importClass(typeInstance)) {
                importableClasses.add(className);
            }
        }
        /*
         * Override longname to short name for inner classes.
         */
        if (typeInstance.getInnerClassHereInfo().isInnerClass()) {
            longNameToShortName.put(className, generateInnerClassShortName(typeInstance));
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

        refClassTypeCache.put(name, typeInstance);
        return typeInstance;
    }

    public List<String> getImports() {
        return ListFactory.newList(importableClasses);
    }
}
