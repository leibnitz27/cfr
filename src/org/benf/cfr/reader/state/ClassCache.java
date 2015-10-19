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
    // We want to avoid generating names which collide with classes.
    // This is a nice simple check.
    private final Set<String> simpleClassNamesSeen = SetFactory.newSet();

    private final DCCommonState dcCommonState;

    public ClassCache(DCCommonState dcCommonState) {
        this.dcCommonState = dcCommonState;
        // TODO:  Not sure I need to do this any more.
        add(TypeConstants.ASSERTION_ERROR.getRawName(), TypeConstants.ASSERTION_ERROR);
        add(TypeConstants.OBJECT.getRawName(), TypeConstants.OBJECT);
        add(TypeConstants.STRING.getRawName(), TypeConstants.STRING);
        add(TypeConstants.ENUM.getRawName(), TypeConstants.ENUM);
    }

    public JavaRefTypeInstance getRefClassFor(String rawClassName) {
        String name = ClassNameUtils.convertFromPath(rawClassName);
        JavaRefTypeInstance typeInstance = refClassTypeCache.get(name);
        if (typeInstance == null) {
            typeInstance = JavaRefTypeInstance.create(name, dcCommonState);
            add(name, typeInstance);
        }
        return typeInstance;
    }

    private void add(String name, JavaRefTypeInstance typeInstance) {
        refClassTypeCache.put(name, typeInstance);
        simpleClassNamesSeen.add(typeInstance.getRawShortName());
    }

    public boolean isClassName(String name) {
        return simpleClassNamesSeen.contains(name);
    }

    public Pair<JavaRefTypeInstance, JavaRefTypeInstance> getRefClassForInnerOuterPair(String rawInnerName, String rawOuterName) {
        String innerName = ClassNameUtils.convertFromPath(rawInnerName);
        String outerName = ClassNameUtils.convertFromPath(rawOuterName);
        JavaRefTypeInstance inner = refClassTypeCache.get(innerName);
        JavaRefTypeInstance outer = refClassTypeCache.get(outerName);
        if (inner != null && outer != null) return Pair.make(inner, outer);
        Pair<JavaRefTypeInstance, JavaRefTypeInstance> pair = JavaRefTypeInstance.createKnownInnerOuter(innerName, outerName, outer, dcCommonState);
        if (inner == null) {
            add(innerName, pair.getFirst());
            inner = pair.getFirst();
        }
        if (outer == null) {
            add(outerName, pair.getSecond());
            outer = pair.getSecond();
        }
        return Pair.make(inner, outer);

    }

    public Collection<JavaRefTypeInstance> getLoadedTypes() {
        return refClassTypeCache.values();
    }
}
