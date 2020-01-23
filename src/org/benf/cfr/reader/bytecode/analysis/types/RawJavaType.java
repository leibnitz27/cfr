package org.benf.cfr.reader.bytecode.analysis.types;

import org.benf.cfr.reader.bytecode.analysis.types.annotated.JavaAnnotatedTypeInstance;
import org.benf.cfr.reader.entities.annotations.AnnotationTableTypeEntry;
import org.benf.cfr.reader.state.ObfuscationTypeMap;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.state.TypeUsageInformation;
import org.benf.cfr.reader.util.DecompilerComments;
import org.benf.cfr.reader.util.StringUtils;
import org.benf.cfr.reader.util.collections.ListFactory;
import org.benf.cfr.reader.util.collections.MapFactory;
import org.benf.cfr.reader.util.collections.SetFactory;
import org.benf.cfr.reader.util.output.Dumper;
import org.benf.cfr.reader.util.output.IllegalIdentifierDump;

import java.util.List;
import java.util.Map;
import java.util.Set;

public enum RawJavaType implements JavaTypeInstance {
    BOOLEAN("boolean", "bl", StackType.INT, true, TypeConstants.boxingNameBoolean, false, false),
    BYTE("byte", "by", StackType.INT, true, TypeConstants.boxingNameByte, true, false),
    CHAR("char", "c", StackType.INT, true, TypeConstants.boxingNameChar, false, false),
    SHORT("short", "s", StackType.INT, true, TypeConstants.boxingNameShort, true, false),
    INT("int", "n", StackType.INT, true, TypeConstants.boxingNameInt, true, false),
    LONG("long", "l", StackType.LONG, true, TypeConstants.boxingNameLong, true, false),
    FLOAT("float", "f", StackType.FLOAT, true, TypeConstants.boxingNameFloat, true, false),
    DOUBLE("double", "d", StackType.DOUBLE, true, TypeConstants.boxingNameDouble, true, false),
    VOID("void", null, StackType.VOID, false, false),
    REF("reference", null, StackType.REF, false, true),  // Don't use for fixedtypeinstance.
    RETURNADDRESS("returnaddress", null, StackType.RETURNADDRESS, false, true),
    RETURNADDRESSORREF("returnaddress or ref", null, StackType.RETURNADDRESSORREF, false, true),
    NULL("null", null, StackType.REF, false, true);  // Null is a special type, sort of.

    private final String name;
    private final String suggestedVarName;
    private final StackType stackType;
    private final boolean usableType;
    private final String boxedName;
    private final boolean isNumber;
    private final boolean isObject;

    private static final Map<RawJavaType, Set<RawJavaType>> implicitCasts = MapFactory.newMap();
    private static final Map<String, RawJavaType> boxingTypes = MapFactory.newMap();
    private static final Map<String, RawJavaType> podLookup = MapFactory.newMap();

    static {
        implicitCasts.put(FLOAT, SetFactory.newSet(DOUBLE));
        implicitCasts.put(LONG, SetFactory.newSet(FLOAT, DOUBLE));
        implicitCasts.put(INT, SetFactory.newSet(LONG, FLOAT, DOUBLE));
        implicitCasts.put(CHAR, SetFactory.newSet(INT, LONG, FLOAT, DOUBLE));
        implicitCasts.put(SHORT, SetFactory.newSet(INT, LONG, FLOAT, DOUBLE));
        implicitCasts.put(BYTE, SetFactory.newSet(SHORT, INT, LONG, FLOAT, DOUBLE));
        for (RawJavaType type : values()) {
            if (type.boxedName != null) {
                boxingTypes.put(type.boxedName, type);
            }
            if (type.usableType) {
                podLookup.put(type.name, type);
            }
        }
        podLookup.put(VOID.name, VOID);
    }

    public static RawJavaType getUnboxedTypeFor(JavaTypeInstance type) {
        String rawName = type.getRawName();
        return boxingTypes.get(rawName);
    }

    public static RawJavaType getPodNamedType(String name) {
        return podLookup.get(name);
    }

    RawJavaType(String name, String suggestedVarName, StackType stackType, boolean usableType, String boxedName, boolean isNumber, boolean objectType) {
        this.name = name;
        this.stackType = stackType;
        this.usableType = usableType;
        this.boxedName = boxedName;
        this.suggestedVarName = suggestedVarName;
        this.isNumber = isNumber;
        this.isObject = objectType;
    }

    RawJavaType(String name, String suggestedVarName, StackType stackType, boolean usableType, boolean objectType) {
        this(name, suggestedVarName, stackType, usableType, null, false, objectType);
    }

    public String getName() {
        return name;
    }

    @Override
    public JavaAnnotatedTypeInstance getAnnotatedInstance() {
        return new Annotated();
    }

    private class Annotated implements JavaAnnotatedTypeInstance {
        private final List<AnnotationTableTypeEntry> entries = ListFactory.newList();

        @Override
        public JavaAnnotatedTypeIterator pathIterator() {
            return new Iterator();
        }

        @Override
        public Dumper dump(Dumper d) {
            for (AnnotationTableTypeEntry entry : entries) {
                entry.dump(d);
                d.print(' ');
            }
            d.dump(RawJavaType.this);
            return d;
        }

        private class Iterator implements JavaAnnotatedTypeIterator {
            // Return this - wrong, but tolerable.
            @Override
            public JavaAnnotatedTypeIterator moveArray(DecompilerComments comments) {
                return this;
            }

            @Override
            public JavaAnnotatedTypeIterator moveBound(DecompilerComments comments) {
                return this;
            }

            @Override
            public JavaAnnotatedTypeIterator moveNested(DecompilerComments comments) {
                return this;
            }

            @Override
            public JavaAnnotatedTypeIterator moveParameterized(int index, DecompilerComments comments) {
                return this;
            }

            @Override
            public void apply(AnnotationTableTypeEntry entry) {
                entries.add(entry);
            }
        }
    }

    @Override
    public StackType getStackType() {
        return stackType;
    }

    @Override
    public boolean isComplexType() {
        return false;
    }

    @Override
    public boolean isObject() { return isObject; }

    @Override
    public JavaGenericRefTypeInstance asGenericRefInstance(JavaTypeInstance other) {
        return null;
    }

    @Override
    public JavaTypeInstance directImplOf(JavaTypeInstance other) {
        return other == this ? this : null;
    }

    @Override
    public JavaTypeInstance deObfuscate(ObfuscationTypeMap obfuscationTypeMap) {
        return this;
    }

    @Override
    public boolean isRaw() {
        return true;
    }

    /*
     * Compare integral type priorities.
     *
     * Int, Bool -> -ve
     * Bool, Int -> +ve
     */
    public int compareTypePriorityTo(RawJavaType other) {
        if (stackType != StackType.INT) throw new IllegalArgumentException();
        if (other.stackType != StackType.INT) throw new IllegalArgumentException();
        return this.ordinal() - other.ordinal();
    }

    public int compareAllPriorityTo(RawJavaType other) {
        return this.ordinal() - other.ordinal();
    }

    @Override
    public boolean isUsableType() {
        return usableType;
    }

    @Override
    public RawJavaType getRawTypeOfSimpleType() {
        return this;
    }

    @Override
    public JavaTypeInstance removeAnArrayIndirection() {
        return VOID;
    }

    @Override
    public JavaTypeInstance getArrayStrippedType() {
        return this;
    }

    @Override
    public JavaTypeInstance getDeGenerifiedType() {
        return this;
    }


    @Override
    public int getNumArrayDimensions() {
        return 0;
    }

    @Override
    public String getRawName() {
        return name;
    }

    @Override
    public String getRawName(IllegalIdentifierDump iid) {
        return getRawName();
    }

    @Override
    public InnerClassInfo getInnerClassHereInfo() {
        return InnerClassInfo.NOT;
    }

    @Override
    public BindingSuperContainer getBindingSupers() {
        return null;
    }

    private boolean implicitlyCastsTo(RawJavaType other) {
        if (other == this) return true;
        Set<RawJavaType> tgt = implicitCasts.get(this);
        if (tgt == null) return false;
        return tgt.contains(other);
    }

    /* Obey the exact specficiation from 5.1.2 JLS */
    @Override
    public boolean implicitlyCastsTo(JavaTypeInstance other, GenericTypeBinder gtb) {
        if (other instanceof RawJavaType) {
            return implicitlyCastsTo((RawJavaType) other);
        }
        if (this == RawJavaType.NULL) return true;
        if (this == RawJavaType.REF) return true;

        if (other instanceof JavaGenericPlaceholderTypeInstance) {
            // We've got dangling generics, probably from using a generic without types.
            return true;
        }
        /*
         * handle boxing.
         */
        if (other instanceof JavaRefTypeInstance) {
            if (other == TypeConstants.OBJECT) {
                return true;
            }
            RawJavaType tgt = getUnboxedTypeFor(other);
            if (tgt == null) {
                // One final special case.
                if (other.getRawName().equals(TypeConstants.boxingNameNumber)) {
                    return isNumber;
                }
                return false;
            }
//            return implicitlyCastsTo(tgt);
            return equals(tgt);
        }
        return false;
    }

    @Override
    public boolean impreciseCanCastTo(JavaTypeInstance other, GenericTypeBinder gtb) {
        if (this.boxedName != null && other instanceof JavaRefTypeInstance) {
            RawJavaType tgt = getUnboxedTypeFor(other);
            if (tgt == null) {
                if (other == TypeConstants.OBJECT) {
                    return true;
                }
                if (other.getRawName().equals(TypeConstants.boxingNameNumber)) {
                    return isNumber;
                }
                return false;
            }
            return implicitlyCastsTo(tgt) || tgt.implicitlyCastsTo(this);
            // Can only cast directly to the 'correct' type.
//            return other.canCastTo(this);
        }
        return true;
    }

    @Override
    public boolean correctCanCastTo(JavaTypeInstance other, GenericTypeBinder gtb) {
        return impreciseCanCastTo(other, gtb);
    }

    @Override
    public String suggestVarName() {
        return suggestedVarName;
    }

    @Override
    public void dumpInto(Dumper d, TypeUsageInformation typeUsageInformation) {
        if (this == NULL) {
            TypeConstants.OBJECT.dumpInto(d, typeUsageInformation);
            return;
        }
        d.print(toString());
    }

    @Override
    public void collectInto(TypeUsageCollector typeUsageCollector) {
    }

    @Override
    public String toString() {
        return name;
    }
}
