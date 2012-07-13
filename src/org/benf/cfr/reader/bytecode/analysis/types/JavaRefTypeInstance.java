package org.benf.cfr.reader.bytecode.analysis.types;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 13/07/2012
 * Time: 08:01
 */
public class JavaRefTypeInstance implements JavaTypeInstance {
    private final String className;

    public JavaRefTypeInstance(String className) {
        this.className = className;
    }

    @Override
    public StackType getStackType() {
        return StackType.REF;
    }

    @Override
    public String toString() {
        return className;
    }
}
