package org.benf.cfr.reader.bytecode.analysis.types;

public interface InnerClassInfo {
    boolean isInnerClass();

    boolean isInnerClassOf(JavaTypeInstance possibleParent);

    boolean isTransitiveInnerClassOf(JavaTypeInstance possibleParent);

    void setHideSyntheticThis();

    boolean isHideSyntheticThis();

    /*
     * I'd rather not have this in the interface, but at the point when we're creating the class, we only
     * know its name, not if it has a 'legit outer'.
     */
    void markMethodScoped(boolean isAnonymous);

    boolean isAnonymousClass();

    boolean isMethodScopedClass();

    JavaRefTypeInstance getOuterClass();

    public static InnerClassInfo NOT = new InnerClassInfo() {
        @Override
        public boolean isInnerClass() {
            return false;
        }

        @Override
        public boolean isAnonymousClass() {
            return false;
        }

        @Override
        public boolean isMethodScopedClass() {
            return false;
        }

        @Override
        public void markMethodScoped(boolean isAnonymous) {
        }

        @Override
        public boolean isInnerClassOf(JavaTypeInstance possibleParent) {
            return false;
        }

        @Override
        public boolean isTransitiveInnerClassOf(JavaTypeInstance possibleParent) {
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
