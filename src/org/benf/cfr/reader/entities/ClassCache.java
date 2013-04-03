package org.benf.cfr.reader.entities;

import org.benf.cfr.reader.bytecode.analysis.types.ClassNameUtils;
import org.benf.cfr.reader.bytecode.analysis.types.JavaRefTypeInstance;
import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.MapFactory;

import java.util.List;
import java.util.Map;

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

    public void markClassNameUsed(String className) {
        int idxlast = className.lastIndexOf('.');
        String partname = idxlast == -1 ? className : className.substring(idxlast + 1);
        if (!shortNameToLongName.containsKey(partname)) {
            shortNameToLongName.put(partname, className);
            longNameToShortName.put(className, partname);
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

        markClassNameUsed(name);
        typeInstance = JavaRefTypeInstance.create(name, this);
        refClassTypeCache.put(name, typeInstance);
        return typeInstance;

    }

    public List<String> getImports() {
        return ListFactory.newList(shortNameToLongName.values());
    }
}
