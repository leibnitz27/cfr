package org.benf.cfr.reader.bytecode.analysis.types;

import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 13/07/2012
 * Time: 07:49
 */
public class MethodPrototype {
    private final List<JavaTypeInstance> args;
    private final JavaTypeInstance result;

    public MethodPrototype(List<JavaTypeInstance> args, JavaTypeInstance result) {
        this.args = args;
        this.result = result;
    }

    public String getPrototype(String methName) {
        StringBuilder sb = new StringBuilder();
        sb.append(result.toString()).append(" ").append(methName).append("(");
        boolean first = true;
        for (JavaTypeInstance arg : args) {
            if (!first) {
                sb.append(", ");
            } else {
                first = false;
            }
            sb.append(arg.toString());
        }
        sb.append(")");
        return sb.toString();
    }
}
