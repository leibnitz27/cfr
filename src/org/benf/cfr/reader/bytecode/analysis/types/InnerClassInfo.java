package org.benf.cfr.reader.bytecode.analysis.types;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 02/04/2013
 * Time: 17:19
 */
public interface InnerClassInfo {
    boolean isInnerClass();

    boolean isInnerClassOf(JavaTypeInstance possibleParent);

    void setHideSyntheticThis();

    boolean isHideSyntheticThis();

    JavaRefTypeInstance getOuterClass();

    public static InnerClassInfo NOT = new InnerClassInfo() {
        @Override
        public boolean isInnerClass() {
            return false;
        }

        @Override
        public boolean isInnerClassOf(JavaTypeInstance possibleParent) {
            return false;
        }

        @Override
        public void setHideSyntheticThis() {
            throw new IllegalStateException();
        }

        @Override
        public JavaRefTypeInstance getOuterClass() {
            throw new IllegalStateException();
        }

        @Override
        public boolean isHideSyntheticThis() {
            return false;
        }
    };
}
