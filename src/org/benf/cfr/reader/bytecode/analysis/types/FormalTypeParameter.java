package org.benf.cfr.reader.bytecode.analysis.types;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 15/08/2012
 * Time: 06:16
 */
public class FormalTypeParameter {
    String name;
    JavaTypeInstance classBound;
    JavaTypeInstance interfaceBound;

    public FormalTypeParameter(String name, JavaTypeInstance classBound, JavaTypeInstance interfaceBound) {
        this.name = name;
        this.classBound = classBound;
        this.interfaceBound = interfaceBound;
    }

    @Override
    public String toString() {
        JavaTypeInstance dispInterface = classBound == null ? interfaceBound : classBound;
        StringBuilder sb = new StringBuilder();
        sb.append(name);
        if (dispInterface != null) {
            if (!"java.lang.Object".equals(dispInterface.getRawName())) {
                sb.append(" extends ");
                sb.append(dispInterface.toString());
            }
        }
        return sb.toString();
    }
}
