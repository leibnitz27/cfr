package org.benf.cfr.reader.bytecode.analysis.types;

import org.benf.cfr.reader.entities.ClassCache;
import org.benf.cfr.reader.entities.ConstantPool;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 13/07/2012
 * Time: 08:01
 */
public class JavaRefTypeInstance implements JavaTypeInstance {
    private final String className;
    private final ClassCache classCache;
    private final InnerClassInfo innerClassInfo; // info about this class AS AN INNER CLASS.

    private JavaRefTypeInstance(String className, ClassCache classCache) {
        InnerClassInfo innerClassInfo = InnerClassInfo.NOT;
        // We should be careful to ONLY check for "$" here, as we'll eliminate it elsewhere.
        if (className.contains("$")) {
            String outer = className.substring(0, className.lastIndexOf('$'));
            JavaRefTypeInstance outerClassTmp = classCache.getRefClassFor(outer);
            innerClassInfo = new RefTypeInnerClassInfo(outerClassTmp);
        }
        this.className = className;
        this.classCache = classCache;
        this.innerClassInfo = innerClassInfo;
    }

    /*
     * Only call from constPool cache.
     */
    public static JavaRefTypeInstance create(String rawClassName, ClassCache classCache) {
        return new JavaRefTypeInstance(rawClassName, classCache);
    }

    @Override
    public StackType getStackType() {
        return StackType.REF;
    }

    @Override
    public String toString() {
        return classCache.getDisplayableClassName(className);
    }

    @Override
    public JavaTypeInstance getArrayStrippedType() {
        return this;
    }

    @Override
    public int getNumArrayDimensions() {
        return 0;
    }

    @Override
    public String getRawName() {
        return className;
    }

    @Override
    public int hashCode() {
        return 31 + className.hashCode();
    }

    @Override
    public InnerClassInfo getInnerClassHereInfo() {
        return innerClassInfo;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof JavaRefTypeInstance)) return false;
        JavaRefTypeInstance other = (JavaRefTypeInstance) o;
        return other.className.equals(className);
    }

    @Override
    public boolean isComplexType() {
        return true;
    }

    @Override
    public boolean isUsableType() {
        return true;
    }

    @Override
    public JavaTypeInstance removeAnArrayIndirection() {
//        if (GlobalArgs.lenient) {
//            return this;
//        } else {
        throw new UnsupportedOperationException("Trying to remove an array indirection on a ref type");
//        }
    }

    @Override
    public JavaTypeInstance getDeGenerifiedType() {
        return this;
    }

    @Override
    public RawJavaType getRawTypeOfSimpleType() {
        return RawJavaType.REF;
    }

    private static class RefTypeInnerClassInfo implements InnerClassInfo {
        private final JavaRefTypeInstance outerClass;
        private boolean hideSyntheticThis = false;

        private RefTypeInnerClassInfo(JavaRefTypeInstance outerClass) {
            this.outerClass = outerClass;
        }

        @Override
        public boolean isInnerClass() {
            return true;
        }

        @Override
        public boolean isInnerClassOf(JavaTypeInstance possibleParent) {
            if (outerClass == null) return false;
            return possibleParent.equals(outerClass);
        }

        @Override
        public void setHideSyntheticThis() {
            hideSyntheticThis = true;
        }

        @Override
        public JavaRefTypeInstance getOuterClass() {
            return outerClass;
        }

        @Override
        public boolean isHideSyntheticThis() {
            return hideSyntheticThis;
        }
    }
}
