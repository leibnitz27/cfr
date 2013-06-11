package org.benf.cfr.reader.entities;

import org.benf.cfr.reader.bytecode.analysis.types.*;
import org.benf.cfr.reader.util.Functional;
import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.MapFactory;
import org.benf.cfr.reader.util.SetFactory;
import org.benf.cfr.reader.util.functors.UnaryFunction;
import org.benf.cfr.reader.util.getopt.CFRState;

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
    private final Set<JavaRefTypeInstance> usedClassSet = SetFactory.newSet();
    private final Map<ConstantPool, Set<JavaTypeInstance>> importableClasses = MapFactory.newLazyMap(new UnaryFunction<ConstantPool, Set<JavaTypeInstance>>() {
        @Override
        public Set<JavaTypeInstance> invoke(ConstantPool arg) {
            return SetFactory.newSet();
        }
    });

    private final CFRState cfrState;
    /*
     * Set just after constructing first type.
     */
    private transient JavaRefTypeInstance analysisType;

    public ClassCache(CFRState cfrState) {
        this.cfrState = cfrState;
        refClassTypeCache.put(TypeConstants.ASSERTION_ERROR.getRawName(), TypeConstants.ASSERTION_ERROR);
        refClassTypeCache.put(TypeConstants.OBJECT.getRawName(), TypeConstants.OBJECT);
        refClassTypeCache.put(TypeConstants.STRING.getRawName(), TypeConstants.STRING);
        refClassTypeCache.put(TypeConstants.ENUM.getRawName(), TypeConstants.ENUM);
    }

    public CFRState getCfrState() {
        return cfrState;
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
    }

    public void markClassNameUsed(ConstantPool cp, JavaRefTypeInstance typeInstance) {
        if (!usedClassSet.add(typeInstance)) return;

        String className = typeInstance.getRawName();
        int idxlast = className.lastIndexOf('.');
        String partname = idxlast == -1 ? className : className.substring(idxlast + 1);
        if (!shortNameToLongName.containsKey(partname)) {
            shortNameToLongName.put(partname, className);
            longNameToShortName.put(className, partname);
            importableClasses.get(cp).add(typeInstance);
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

    public JavaRefTypeInstance getRefClassFor(ConstantPool cp, String rawClassName) {
        String name = ClassNameUtils.convertFromPath(rawClassName);
        JavaRefTypeInstance typeInstance = refClassTypeCache.get(name);
        if (typeInstance != null) {
            // This is a bit messy, as we will only hit here on an unused type if it's
            // a pre-cooked one.
            markClassNameUsed(cp, typeInstance);
            return typeInstance;
        }

        typeInstance = JavaRefTypeInstance.create(cp, name, this);

        refClassTypeCache.put(name, typeInstance);
        return typeInstance;
    }

    public Set<JavaTypeInstance> getImports(List<ConstantPool> constantPoolList) {
        Set<JavaTypeInstance> imports = SetFactory.newSet();
        for (ConstantPool cp : constantPoolList) {
            imports.addAll(importableClasses.get(cp));
        }
        return imports;
    }
}
