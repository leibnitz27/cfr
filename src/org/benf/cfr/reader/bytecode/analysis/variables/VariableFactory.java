package org.benf.cfr.reader.bytecode.analysis.variables;

import org.benf.cfr.reader.bytecode.BytecodeMeta;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.LocalVariable;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.MethodPrototype;
import org.benf.cfr.reader.bytecode.analysis.types.RawJavaType;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.util.collections.MapFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class VariableFactory {
    private final VariableNamer variableNamer;
    private final Map<Integer, InferredJavaType> typedArgs;
    private final Set<Integer> clashes;
    private final Method method;
    private int ignored;

    private final Map<LValue, LValue> cache = MapFactory.newMap();

    public VariableFactory(Method method, BytecodeMeta bytecodeMeta) {
        this.variableNamer = method.getVariableNamer();
        this.clashes = bytecodeMeta.getLivenessClashes();
        MethodPrototype methodPrototype = method.getMethodPrototype();
        List<JavaTypeInstance> args = methodPrototype.getArgs();
        this.typedArgs = MapFactory.newMap();
        int offset = 0;
        if (methodPrototype.isInstanceMethod()) {
            JavaTypeInstance thisType = method.getClassFile().getClassType();
            typedArgs.put(offset++, new InferredJavaType(thisType, InferredJavaType.Source.UNKNOWN, true));
        }
        for (JavaTypeInstance arg : args) {
            typedArgs.put(offset, new InferredJavaType(arg, InferredJavaType.Source.UNKNOWN, true));
            offset += arg.getStackType().getComputationCategory();
        }
        if (methodPrototype.parametersComputed()) {
            for (LocalVariable localVariable : methodPrototype.getComputedParameters()) {
                cache.put(localVariable, localVariable);
            }
        }
        this.method = method;
    }

    public JavaTypeInstance getReturn() {
        return method.getMethodPrototype().getReturnType();
    }

    public LValue ignoredVariable(InferredJavaType type) {
        return new LocalVariable("cfr_ignored_" + ignored++, type);
    }
    /*
     * NB: idx is slot, i.e. offset.
     */
    public LValue localVariable(int stackPosition, Ident ident, int origCodeRawOffset) {
        if (ident == null) {
            // This isn't right, and indicates a failure, possibly in synthetic argument deduction.
            // however, we can recover from here.
            ident = new Ident(stackPosition, -1);
        }
        InferredJavaType varType = ident.getIdx() == 0 ? typedArgs.get(stackPosition) : null;
        if (varType == null) {
            varType = new InferredJavaType(RawJavaType.VOID, InferredJavaType.Source.UNKNOWN);
        }

        LValue tmp = new LocalVariable(stackPosition, ident, variableNamer, origCodeRawOffset,
                clashes.contains(stackPosition)
                // TODO : This is a hack disabling explicit clashes, to avoid breaking Tower. (see tests)
                // However, it will need to be fixed to deal with bug #87
                && typedArgs.get(stackPosition) == null,
                varType);
        LValue val = cache.get(tmp);
        if (val == null) {
            cache.put(tmp, tmp);
            val = tmp;
        }
        return val;
    }

    public void mutatingRenameUnClash(LocalVariable toRename) {
        variableNamer.mutatingRenameUnClash(toRename.getName());
    }
}
