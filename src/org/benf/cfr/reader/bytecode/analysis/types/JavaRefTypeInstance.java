package org.benf.cfr.reader.bytecode.analysis.types;

import org.benf.cfr.reader.entities.ClassCache;
import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.entities.ConstantPool;
import org.benf.cfr.reader.util.getopt.CFRState;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 13/07/2012
 * Time: 08:01
 */
public class JavaRefTypeInstance implements JavaTypeInstance {
    private final String className;
    private final String displayableName;
    private final InnerClassInfo innerClassInfo; // info about this class AS AN INNER CLASS.
    private final CFRState cfrState;

    private JavaRefTypeInstance(ConstantPool cp, String className, ClassCache classCache) {
        InnerClassInfo innerClassInfo = InnerClassInfo.NOT;
        // We should be careful to ONLY check for "$" here, as we'll eliminate it elsewhere.
        if (className.contains("$")) {
            String outer = className.substring(0, className.lastIndexOf('$'));
            JavaRefTypeInstance outerClassTmp = classCache.getRefClassFor(cp, outer);
            innerClassInfo = new RefTypeInnerClassInfo(outerClassTmp);
        }
        this.className = className;
        this.innerClassInfo = innerClassInfo;
        classCache.markClassNameUsed(cp, this);
        this.displayableName = classCache.getDisplayableClassName(className);
        this.cfrState = classCache.getCfrState();
    }

    private JavaRefTypeInstance(String className, String displayableName) {
        this.innerClassInfo = InnerClassInfo.NOT;
        this.className = className;
        this.displayableName = displayableName;
        this.cfrState = null;
    }

    /*
     * Only call from constPool cache.
     */
    public static JavaRefTypeInstance create(ConstantPool cp, String rawClassName, ClassCache classCache) {
        return new JavaRefTypeInstance(cp, rawClassName, classCache);
    }

    /*
     * ONLY call from TypeConstants.
     */
    public static JavaRefTypeInstance createTypeConstant(String rawClassName, String displayableName) {
        return new JavaRefTypeInstance(rawClassName, displayableName);
    }

    @Override
    public StackType getStackType() {
        return StackType.REF;
    }

    @Override
    public String toString() {
        return displayableName;
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

    public ClassFile getClassFile() {
        ClassFile classFile = cfrState.getClassFile(this, false);
        return classFile;
    }

    private static class RefTypeInnerClassInfo implements InnerClassInfo {
        private final JavaRefTypeInstance outerClass;
        private boolean isAnonymous = false;
        private boolean hideSyntheticThis = false;

        private RefTypeInnerClassInfo(JavaRefTypeInstance outerClass) {
            this.outerClass = outerClass;
        }

        @Override
        public boolean isInnerClass() {
            return true;
        }

        @Override
        public boolean isAnoynmousInnerClass() {
            return isAnonymous;
        }

        @Override
        public void markAnonymous() {
            isAnonymous = true;
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
