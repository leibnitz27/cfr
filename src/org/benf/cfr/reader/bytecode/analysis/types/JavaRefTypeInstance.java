package org.benf.cfr.reader.bytecode.analysis.types;

import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.benf.cfr.reader.bytecode.analysis.types.annotated.JavaAnnotatedTypeInstance;
import org.benf.cfr.reader.entities.AccessFlag;
import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.entities.annotations.AnnotationTableEntry;
import org.benf.cfr.reader.state.DCCommonState;
import org.benf.cfr.reader.state.ObfuscationTypeMap;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.state.TypeUsageInformation;
import org.benf.cfr.reader.util.*;
import org.benf.cfr.reader.util.annotation.Nullable;
import org.benf.cfr.reader.util.collections.ListFactory;
import org.benf.cfr.reader.util.collections.MapFactory;
import org.benf.cfr.reader.util.output.Dumper;
import org.benf.cfr.reader.util.output.IllegalIdentifierDump;
import org.benf.cfr.reader.util.output.ToStringDumper;
import org.benf.cfr.reader.util.output.TypeContext;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class JavaRefTypeInstance implements JavaTypeInstance {
    private final String className;
    private String shortName; // may not be unique
    private String suggestedVarName;
    private InnerClassInfo innerClassInfo; // info about this class AS AN INNER CLASS.
    //    private final Options options;
    private final DCCommonState dcCommonState; // Shouldn't need this here...
    private BindingSuperContainer cachedBindingSupers = BindingSuperContainer.POISON;

    private JavaRefTypeInstance(final String className, DCCommonState dcCommonState) {
        this.innerClassInfo = InnerClassInfo.NOT;
        this.dcCommonState = dcCommonState;
        /*
         * This /MAY/ be a red herring.  It's possible for a class name to contain a dollar
         * without it being an inner class.
         */
        if (className.contains(MiscConstants.INNER_CLASS_SEP_STR)) {
            int idx = className.lastIndexOf(MiscConstants.INNER_CLASS_SEP_CHAR);

            if (idx == className.length()-1) {
                /*
                 * if this is the case, it's likely to be a non-inner scala class.....
                 * Would be nice to have a better way to verify though.
                 */
                MiscUtils.handyBreakPoint();
            } else {
                String outer = className.substring(0, idx);
                JavaRefTypeInstance outerClassTmp = dcCommonState.getClassCache().getRefClassFor(outer);
                innerClassInfo = new RefTypeInnerClassInfo(outerClassTmp);
            }
        }
        this.className = className;
        this.shortName = getShortName(className, innerClassInfo);
    }

    /*
     * This should only be necessary when dealing with classes where we can't infer this at creation time.
     */
    public void setUnexpectedInnerClassOf(JavaRefTypeInstance parent) {
        this.innerClassInfo = new RefTypeInnerClassInfo(parent);
    }

    private JavaRefTypeInstance(final String className, final JavaRefTypeInstance knownOuter, DCCommonState dcCommonState) {
        this.className = className;
        this.dcCommonState = dcCommonState;
        String innerSub = className.substring(knownOuter.className.length());
        /*
         * Now, we have to make an assumption at this point that if the first character of innerSub is a $ (Sep)
         * that we should replace it.  This isn't mandated by anything, so we could create illegals again here. :P
         *
         */
        if (innerSub.charAt(0) == MiscConstants.INNER_CLASS_SEP_CHAR) {
            innerSub = innerSub.substring(1);
        }
        this.innerClassInfo = new RefTypeInnerClassInfo(knownOuter);
        this.shortName = innerSub;
    }

    @Override
    public JavaAnnotatedTypeInstance getAnnotatedInstance() {
        JavaRefTypeInstance rti = this;
        Annotated prev = null;
        boolean couldLoadOuterClasses = true;

        do {
            InnerClassInfo ici = rti.getInnerClassHereInfo();
            boolean isInner = ici.isInnerClass();
            prev = new Annotated(prev, rti);
            /*
             * JLS 9.7.4 ("admissible"):
             * Annotation relates to runtime object, so if inner class is non-static, an instance of outer
             * class exists as runtime object and can therefore be annotated. However, for static nested
             * classes no instance of outer class exists, so outer class is just a name and cannot be
             * annotated.
             */
            ClassFile classFile = rti.getClassFile();
            if (isInner && (classFile == null || !classFile.testAccessFlag(AccessFlag.ACC_STATIC))) {
                rti = ici.getOuterClass();

                // Annotation placement might be incorrect if determining whether outer
                // is static is not possible
                if (couldLoadOuterClasses && classFile == null) {
                    couldLoadOuterClasses = false;
                }
            } else {
                rti = null;
            }
        } while (rti != null);

        if (!couldLoadOuterClasses) {
            prev.setComment(DecompilerComment.BAD_ANNOTATION_ON_INNER);
        }

        return prev;
    }

    private static class Annotated implements JavaAnnotatedTypeInstance {
        private final List<AnnotationTableEntry> entries = ListFactory.newList();
        private final Annotated inner;
        private final JavaRefTypeInstance outerThis;
        private DecompilerComment nullableComment = null;

        private Annotated(Annotated inner, JavaRefTypeInstance outerThis) {
            this.inner = inner;
            this.outerThis = outerThis;
        }

        public void setComment(DecompilerComment comment) {
            this.nullableComment = comment;
        }

        public JavaAnnotatedTypeIterator pathIterator() {
            return new Iterator();
        }

        private void dump(Dumper d, boolean hasDumpedType) {
            if (nullableComment != null) {
                d.dump(nullableComment);
            }

            if (!entries.isEmpty()) {
                if (hasDumpedType) d.print(' ');
                for (AnnotationTableEntry entry : entries) {
                    entry.dump(d);
                    d.print(' ');
                }
            }

            // Only dump type for first actually annotated type.
            // E.g. with A, B and C all being inner (= non-static) classes, having
            // `A.B.@TypeUse C` should dump `@TypeUse C`
            // See AnnotationTestInnerDisplay.
            if (!hasDumpedType && entries.isEmpty() && inner != null) {
                inner.dump(d, false);
            } else {
                if (!hasDumpedType) {
                    d.dump(outerThis);
                } else {
                    d.print(outerThis.getRawShortName());
                }
                if (inner != null) {
                    d.print('.');
                    inner.dump(d, true);
                }
            }
        }

        Annotated getFirstWithEntries() {
            if (inner == null) return null;
            if (entries.isEmpty()) return inner.getFirstWithEntries();
            return this;
        }

        @Override
        public Dumper dump(Dumper d) {
            boolean hasDumpedType = false;

            // We can only take advantage of hasDumpedType if the first type that will be
            // dumped has an explicit match to it's raw name.  Otherwise, we will appear to
            // move annotations to an outer class.
            // (See AnnotationTestCode5)
            Annotated firstEntryType = getFirstWithEntries();
            if (firstEntryType != null) {
                JavaRefTypeInstance typ = firstEntryType.outerThis;
                String display = d.getTypeUsageInformation().getName(typ, TypeContext.None);
                String raw = typ.getRawShortName();
                if (!raw.equals(display)) hasDumpedType = true;
            }
            dump(d, hasDumpedType);
            return d;
        }

        private class Iterator extends JavaAnnotatedTypeIterator.BaseAnnotatedTypeIterator {
            @Override
            public JavaAnnotatedTypeIterator moveNested(DecompilerComments comments) {
                return inner.pathIterator();
            }

            @Override
            public void apply(AnnotationTableEntry entry) {
                entries.add(entry);
            }
        }

    }

    @Override
    public boolean isObject() {
        return true;
    }

    public void markNotInner() {
        this.innerClassInfo = InnerClassInfo.NOT;
        this.shortName = getShortName(className, innerClassInfo);
        this.suggestedVarName = null;
    }

    @Override
    public String suggestVarName() {
        if (suggestedVarName != null) return suggestedVarName;

        String displayName = this.shortName;
        if (displayName.isEmpty()) return null;
        char[] chars = displayName.toCharArray();
        int x;
        int len = chars.length;
        for (x = 0; x < len; ++x) {
            char c = chars[x];
            if (c >= '0' && c <= '9') continue;
            break;
        }
        if (x >= len) return null;
        chars[x] = Character.toLowerCase(chars[x]);
        suggestedVarName = new String(chars, x, len - x);
        return suggestedVarName;
    }

    private JavaRefTypeInstance(String className, String displayableName, JavaRefTypeInstance[] supers) {
        this.innerClassInfo = InnerClassInfo.NOT;
        this.dcCommonState = null; // TODO : Wrong.
        this.className = className;
        this.shortName = displayableName;
        Map<JavaRefTypeInstance, JavaGenericRefTypeInstance> tmp = MapFactory.newMap();
        Map<JavaRefTypeInstance, BindingSuperContainer.Route> routes = MapFactory.newMap();
        for (JavaRefTypeInstance supr : supers) {
            tmp.put(supr, null);
            routes.put(supr, BindingSuperContainer.Route.EXTENSION);
        }
        tmp.put(this, null);

        this.cachedBindingSupers = new BindingSuperContainer(null, tmp, routes);
    }

    /*
     * Only call from constPool cache.
     */
    public static JavaRefTypeInstance create(String rawClassName, DCCommonState dcCommonState) {
        return new JavaRefTypeInstance(rawClassName, dcCommonState);
    }

    public static Pair<JavaRefTypeInstance, JavaRefTypeInstance> createKnownInnerOuter(String inner, String outer, JavaRefTypeInstance outerType, DCCommonState dcCommonState) {
        if (outerType == null) outerType = new JavaRefTypeInstance(outer, dcCommonState);
        JavaRefTypeInstance innerType;
        if (!inner.startsWith(outer)) {
            // Handle the illegal inner/outer combo.
            innerType = new JavaRefTypeInstance(inner, dcCommonState);
        } else {
            innerType = new JavaRefTypeInstance(inner, outerType, dcCommonState);
        }
        return Pair.make(innerType, outerType);
    }

    /**
     * ONLY call when creating type constants.
     */
    static JavaRefTypeInstance createTypeConstant(String rawClassName, String displayableName, JavaRefTypeInstance... supers) {
        return new JavaRefTypeInstance(rawClassName, displayableName, supers);
    }

    /**
     * ONLY call when creating type constants.
     */
    public static JavaRefTypeInstance createTypeConstant(String rawClassName, JavaRefTypeInstance... supers) {
        return createTypeConstant(rawClassName, getShortName(rawClassName), supers);
    }

    /**
     * ONLY call when creating type constants.
     */
    static JavaRefTypeInstance createTypeConstantWithObjectSuper(String rawClassName) {
        return createTypeConstant(rawClassName, getShortName(rawClassName), TypeConstants.OBJECT);
    }

    @Override
    public StackType getStackType() {
        return StackType.REF;
    }

    @Override
    public void dumpInto(Dumper d, TypeUsageInformation typeUsageInformation, TypeContext typeContext) {
        String res = typeUsageInformation.getName(this, typeContext);
        if (res == null) throw new IllegalStateException();
        d.print(res);
    }

    public String getPackageName() {
        return ClassNameUtils.getPackageAndClassNames(this).getFirst();
    }

    @Override
    public String toString() {
        return new ToStringDumper().dump(this).toString();
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

    public String getRawShortName() {
        return shortName;
    }

    @Override
    public String getRawName(IllegalIdentifierDump iid) {
        if (iid != null) {
            String replaceShortName = getRawShortName(iid);
            //noinspection StringEquality
            if (shortName == replaceShortName) {
                return className;
            }
            return className.substring(0, className.length() - shortName.length()) + replaceShortName;
        }
        return getRawName();
    }

    public String getRawShortName(IllegalIdentifierDump iid) {
        if (iid != null) {
            return iid.getLegalShortName(shortName);
        }
        return getRawShortName();
    }

    @Override
    public int hashCode() {
        return 31 + className.hashCode();
    }

    @Override
    public InnerClassInfo getInnerClassHereInfo() {
        return innerClassInfo;
    }

    public void forceBindingSupers(BindingSuperContainer bindingSuperContainer) {
        cachedBindingSupers = bindingSuperContainer;
    }

    @Override
    public BindingSuperContainer getBindingSupers() {
        if (cachedBindingSupers != BindingSuperContainer.POISON) return cachedBindingSupers;
        try {
            ClassFile classFile = getClassFile();
            cachedBindingSupers = classFile == null ? null : classFile.getBindingSupers();
        } catch (CannotLoadClassException e) {
            cachedBindingSupers = null;
        }
        return cachedBindingSupers;
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
        /*
         * We could end up with this if we have accidental false slot sharing.
         * Don't fail because of it.
         */
        return this;
    }

    @Override
    public JavaTypeInstance getDeGenerifiedType() {
        return this;
    }

    @Override
    public RawJavaType getRawTypeOfSimpleType() {
        return RawJavaType.REF;
    }

    @Override
    public JavaTypeInstance deObfuscate(ObfuscationTypeMap obfuscationTypeMap) {
        return obfuscationTypeMap.get(this);
    }

    @Override
    public boolean implicitlyCastsTo(JavaTypeInstance other, @Nullable GenericTypeBinder gtb) {
        if (this.equals(other)) return true;
        if (other instanceof RawJavaType) {
            /*
             * If this is boxed, we can unbox, and cast up.
             */
            RawJavaType thisAsRaw = RawJavaType.getUnboxedTypeFor(this);
            if (thisAsRaw != null) {
                return thisAsRaw.implicitlyCastsTo(other, gtb);
            }
        }
        if (gtb != null && other instanceof JavaGenericPlaceholderTypeInstance) {
            other = gtb.getBindingFor(other);
            if (!(other instanceof JavaGenericPlaceholderTypeInstance)) {
                return implicitlyCastsTo(other, gtb);
            }
        }
        if (other instanceof JavaGenericPlaceholderTypeInstance) {
            return false;
        }
        JavaTypeInstance otherRaw = other.getDeGenerifiedType();
        BindingSuperContainer thisBindingSuper = this.getBindingSupers();
        if (thisBindingSuper == null) {
            // TODO : If we ever get value types, this won't be valid.
            if (otherRaw == TypeConstants.OBJECT) return true;
            return false;
        }
        return thisBindingSuper.containsBase(otherRaw);
    }

    @Override
    public JavaTypeInstance directImplOf(JavaTypeInstance other) {
        return other == this ? this : null;
    }

    /*
     * Fixme - shouldn't this use binding supercontainer?
     */
    @Override
    public boolean impreciseCanCastTo(JavaTypeInstance other, GenericTypeBinder gtb) {
        if (this == other || this.equals(other)) return true;
        if (other instanceof RawJavaType) {
            /*
             * If this is boxed, we can unbox, and cast up.
             */
            RawJavaType thisAsRaw = RawJavaType.getUnboxedTypeFor(this);
            if (thisAsRaw != null) {
                return thisAsRaw.equals(other);
            }
            return true;
        }
        return true;
    }

    @Override
    public boolean correctCanCastTo(JavaTypeInstance other, GenericTypeBinder gtb) {
        if (this == other || this.equals(other)) return true;
        if (other instanceof RawJavaType) {
            /*
             * If this is boxed, we can unbox, and cast up.
             */
            RawJavaType thisAsRaw = RawJavaType.getUnboxedTypeFor(this);
            if (thisAsRaw != null) {
                return thisAsRaw.equals(other);
            }
            return true;
        }
        BindingSuperContainer bindingSuperContainer = getBindingSupers();
        if (bindingSuperContainer == null) {
            // Don't know, so have to assume so.
            return true;
        }
        if (bindingSuperContainer.containsBase(other)) {
            return true;
        }
        bindingSuperContainer = other.getBindingSupers();
        if (bindingSuperContainer == null) {
            return true;
        }
        if (bindingSuperContainer.containsBase(this)) {
            return true;
        }
        return false;
    }


    public ClassFile getClassFile() {
        if (dcCommonState == null) return null;
        try {
            ClassFile classFile = dcCommonState.getClassFile(this);
            return classFile;
        } catch (CannotLoadClassException e) {
            return null;
        }
    }

    private static String getShortName(String fullClassName) {
        int idxlast = fullClassName.lastIndexOf('.');
        String partname = idxlast == -1 ? fullClassName : fullClassName.substring(idxlast + 1);
        return partname;
    }

    private static String getShortName(String fullClassName, InnerClassInfo innerClassInfo) {
        if (innerClassInfo.isInnerClass()) {
            fullClassName = fullClassName.replace(MiscConstants.INNER_CLASS_SEP_CHAR, '.');
        }
        return getShortName(fullClassName);
    }

    @Override
    public void collectInto(TypeUsageCollector typeUsageCollector) {
        typeUsageCollector.collectRefType(this);
    }

    @Override
    public JavaGenericRefTypeInstance asGenericRefInstance(JavaTypeInstance other) {
        return null;
    }

    @Override
    public boolean isRaw() {
        return false;
    }

    private static class RefTypeInnerClassInfo implements InnerClassInfo {
        private final JavaRefTypeInstance outerClass;
        private boolean isAnonymous = false;
        private boolean isMethodScoped = false;
        private boolean hideSyntheticThis = false;
        private boolean hideSyntheticFriendClass = false;

        private RefTypeInnerClassInfo(JavaRefTypeInstance outerClass) {
            this.outerClass = outerClass;
        }

        @Override
        public
        void collectTransitiveDegenericParents(Set<JavaTypeInstance> parents) {
            parents.add(outerClass);
            outerClass.getInnerClassHereInfo().collectTransitiveDegenericParents(parents);
        }

        @Override
        public boolean getFullInnerPath(StringBuilder sb) {
            if (outerClass.getInnerClassHereInfo().getFullInnerPath(sb)) {
                sb.append(outerClass.shortName).append('.');
            }
            return true;
        }

        @Override
        public boolean isInnerClass() {
            return true;
        }

        @Override
        public boolean isAnonymousClass() {
            return isAnonymous;
        }

        @Override
        public boolean isMethodScopedClass() {
            return isMethodScoped;
        }

        @Override
        public void markMethodScoped(boolean isAnonymous) {
            this.isAnonymous = isAnonymous;
            this.isMethodScoped = true;
        }

        @Override
        public boolean isInnerClassOf(JavaTypeInstance possibleParent) {
            if (outerClass == null) return false;
            return possibleParent.equals(outerClass);
        }

        @Override
        public boolean isTransitiveInnerClassOf(JavaTypeInstance possibleParent) {
            if (outerClass == null) return false;
            if (possibleParent.equals(outerClass)) return true;
            InnerClassInfo upper = outerClass.getInnerClassHereInfo();
            if (!upper.isInnerClass()) return false;
            return upper.isTransitiveInnerClassOf(possibleParent);
        }

        @Override
        public void setHideSyntheticThis() {
            hideSyntheticThis = true;
        }

        @Override
        public void hideSyntheticFriendClass() {
            hideSyntheticFriendClass = true;
        }

        @Override
        public boolean isSyntheticFriendClass() {
            return hideSyntheticFriendClass;
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
