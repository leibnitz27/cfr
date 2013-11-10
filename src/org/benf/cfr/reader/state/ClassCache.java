package org.benf.cfr.reader.state;

import org.benf.cfr.reader.bytecode.analysis.types.*;
import org.benf.cfr.reader.entities.constantpool.ConstantPool;
import org.benf.cfr.reader.state.DCCommonState;
import org.benf.cfr.reader.util.MapFactory;
import org.benf.cfr.reader.util.SetFactory;
import org.benf.cfr.reader.util.functors.UnaryFunction;
import org.benf.cfr.reader.util.getopt.Options;

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

    private final Map<String, JavaRefTypeInstance> refClassTypeCache = MapFactory.newMap();

    private final DCCommonState dcCommonState;

    public ClassCache(DCCommonState dcCommonState) {
        this.dcCommonState = dcCommonState;
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

}
