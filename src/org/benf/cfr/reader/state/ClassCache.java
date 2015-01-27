package org.benf.cfr.reader.state;

import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.benf.cfr.reader.bytecode.analysis.types.*;
import org.benf.cfr.reader.entities.constantpool.ConstantPool;
import org.benf.cfr.reader.state.DCCommonState;
import org.benf.cfr.reader.util.MapFactory;
import org.benf.cfr.reader.util.SetFactory;
import org.benf.cfr.reader.util.functors.UnaryFunction;
import org.benf.cfr.reader.util.getopt.Options;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ClassCache {

    private final Map<String, JavaRefTypeInstance> refClassTypeCache = MapFactory.newMap();

    private final DCCommonState dcCommonState;

    public ClassCache(DCCommonState dcCommonState) {
        this.dcCommonState = dcCommonState;
        // TODO:  Not sure I need to do this any more.
        refClassTypeCache.put(TypeConstants.ASSERTION_ERROR.getRawName(), TypeConstants.ASSERTION_ERROR);
        refClassTypeCache.put(TypeConstants.OBJECT.getRawName(), TypeConstants.OBJECT);
        refClassTypeCache.put(TypeConstants.STRING.getRawName(), TypeConstants.STRING);
        refClassTypeCache.put(TypeConstants.ENUM.getRawName(), TypeConstants.ENUM);
    }

    public JavaRefTypeInstance getRefClassFor(String rawClassName) {
        String name = ClassNameUtils.convertFromPath(rawClassName);
        JavaRefTypeInstance typeInstance = refClassTypeCache.get(name);
        if (typeInstance == null) {
            typeInstance = JavaRefTypeInstance.create(name, dcCommonState);
            refClassTypeCache.put(name, typeInstance);
        }
        return typeInstance;
    }

    public Pair<JavaRefTypeInstance, JavaRefTypeInstance> getRefClassForInnerOuterPair(String rawInnerName, String rawOuterName) {
        String innerName = ClassNameUtils.convertFromPath(rawInnerName);
        String outerName = ClassNameUtils.convertFromPath(rawOuterName);
        JavaRefTypeInstance inner = refClassTypeCache.get(innerName);
        JavaRefTypeInstance outer = refClassTypeCache.get(outerName);
        if (inner != null && outer != null) return Pair.make(inner, outer);
        Pair<JavaRefTypeInstance, JavaRefTypeInstance> pair = JavaRefTypeInstance.createKnownInnerOuter(innerName, outerName, outer, dcCommonState);
        if (inner == null) {
            refClassTypeCache.put(innerName, pair.getFirst());
            inner = pair.getFirst();
        }
        if (outer == null) {
            refClassTypeCache.put(outerName, pair.getSecond());
            outer = pair.getSecond();
        }
        return Pair.make(inner, outer);

    }

    public Collection<JavaRefTypeInstance> getLoadedTypes() {
        return refClassTypeCache.values();
    }
}
