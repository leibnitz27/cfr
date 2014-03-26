package org.benf.cfr.reader.bytecode.analysis.variables;

import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.LocalVariable;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.MethodPrototype;
import org.benf.cfr.reader.bytecode.analysis.types.RawJavaType;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.util.ConfusedCFRException;
import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.MapFactory;
import org.benf.cfr.reader.util.MiscConstants;

import java.util.List;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 20/07/2012
 * Time: 18:15
 */
public class VariableFactory {
    private final VariableNamer variableNamer;
    private final Map<Integer, InferredJavaType> typedArgs;
    private final Method method;

    private final Map<LValue, LValue> cache = MapFactory.newMap();

    public VariableFactory(Method method) {
        this.variableNamer = method.getVariableNamer();
        if (method == null) {
            throw new ConfusedCFRException("No method signature for a variable factory");
        }
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
        this.method = method;
    }

    public JavaTypeInstance getReturn() {
        return method.getMethodPrototype().getReturnType();
    }

    /*
     * NB: idx is slot, i.e. offset.
     */
    public LValue localVariable(int stackPosition, Ident ident, int origCodeRawOffset, boolean guessedFinal) {
        if (ident == null) {
            throw new IllegalStateException();
        }
        InferredJavaType varType = typedArgs.get(stackPosition);
        if (varType == null) {
            // Shouldn't happen, but protect.
            varType = new InferredJavaType(RawJavaType.VOID, InferredJavaType.Source.UNKNOWN);
        }
        LValue tmp = new LocalVariable(stackPosition, ident, variableNamer, origCodeRawOffset, varType, guessedFinal);
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
