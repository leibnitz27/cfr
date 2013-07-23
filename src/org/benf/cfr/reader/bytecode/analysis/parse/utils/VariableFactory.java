package org.benf.cfr.reader.bytecode.analysis.parse.utils;

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
    private final List<InferredJavaType> typedArgs;
    private final Method method;

    private final Map<LValue, LValue> cache = MapFactory.newMap();

    public VariableFactory(Method method) {
        this.variableNamer = method.getVariableNamer();
        if (method == null) {
            throw new ConfusedCFRException("No method signature for a variable factory");
        }
        MethodPrototype methodPrototype = method.getMethodPrototype();
        List<JavaTypeInstance> args = methodPrototype.getArgs();
        this.typedArgs = ListFactory.newList();
        if (methodPrototype.isInstanceMethod()) {
            JavaTypeInstance thisType = method.getClassFile().getClassType();
            typedArgs.add(new InferredJavaType(thisType, InferredJavaType.Source.UNKNOWN, true));
            /*
             * And hey, let's hardcode that the name is 'this' too.
             */
            variableNamer.forceName(0, MiscConstants.THIS);
        }
        for (JavaTypeInstance arg : args) {
            typedArgs.add(new InferredJavaType(arg, InferredJavaType.Source.UNKNOWN, true));
        }
        this.method = method;
    }

    public JavaTypeInstance getReturn() {
        return method.getMethodPrototype().getReturnType();
    }

    public LValue localVariable(int idx, int origRawOffset) {
        InferredJavaType varType;
        if (idx < typedArgs.size()) {
            varType = typedArgs.get(idx);
        } else {
            varType = new InferredJavaType(RawJavaType.VOID, InferredJavaType.Source.UNKNOWN);
        }
        LValue tmp = new LocalVariable(idx, variableNamer, origRawOffset, varType);
        LValue val = cache.get(tmp);
        if (val == null) {
            cache.put(tmp, tmp);
            val = tmp;
        }
        return val;
    }
}
