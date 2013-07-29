package org.benf.cfr.reader.bytecode.analysis.types.discovery;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ArithOp;
import org.benf.cfr.reader.bytecode.analysis.types.*;
import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.util.ConfusedCFRException;
import org.benf.cfr.reader.util.Troolean;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 20/07/2012
 * <p/>
 * Multiple expressions / lvalues will have pointers to a single instance of this - at type changing boundaries,
 * we will explicitly create a new one.
 * <p/>
 * Thus if we have
 * <p/>
 * a = 94
 * b = a
 * c = b
 * charfunction((no cast)c)
 * <p/>
 * we know that c is appropriate to be passed directly to a char function (i.e. a char).  So we can update the
 * type which is held by c=b=a=94.
 * <p/>
 * however, if we have
 * <p/>
 * a = 94
 * b = a
 * c = (i2c)b
 * charfunction((no cast)c), c will have a forced char type, we won't need to update it.
 * <p/>
 * Note that this works only for narrowing functions, as a char will be passed by the JVM to an int function without
 * extension.
 */
public class InferredJavaType {
    public enum Source {
        TEST,
        UNKNOWN,
        LITERAL,
        FIELD,
        FUNCTION,
        OPERATION,
        EXPRESSION,
        INSTRUCTION, // Instr returns type which guarantees this (eg arraylength returns int).
        GENERICCALL
    }


    private static int global_id = 0;

    private enum ClashState {
        None,
        Clash,
        Resolved
    }

    private static class IJTInternal {

        private boolean isDelegate = false;
        private final boolean locked;
        private ClashState clashState;
        // When not delegating
        private JavaTypeInstance type;

        private final Source source;
        private final int id;
        // When delegating
        private IJTInternal delegate;

        private IJTInternal(JavaTypeInstance type, Source source, boolean locked) {
            this.type = type;
            this.source = source;
            this.id = global_id++;
            this.locked = locked;
            this.clashState = ClashState.None;
        }

        private IJTInternal(IJTInternal delegate, boolean locked, ClashState markedBad) {
            this.isDelegate = true;
            this.locked = locked;
            this.type = null;
            this.source = null;
            this.id = global_id++;
            this.delegate = delegate;
            this.clashState = markedBad;
        }

        public RawJavaType getRawType() {
            // Think this might bite me later?
            if (isDelegate) {
                return delegate.getRawType();
            } else {
                return type.getRawTypeOfSimpleType();
            }
        }

        public JavaTypeInstance getJavaTypeInstance() {
            if (isDelegate) {
                return delegate.getJavaTypeInstance();
            } else {
                return type;
            }
        }

        public Source getSource() {
            if (isDelegate) {
                return delegate.getSource();
            } else {
                return source;
            }
        }

        public int getFinalId() {
            if (isDelegate) {
                return delegate.getFinalId();
            } else {
                return id;
            }
        }

        public ClashState getClashState() {
            if (clashState != null) return clashState;
            if (isDelegate) return delegate.getClashState();
            return ClashState.None;
        }

        public void mkDelegate(IJTInternal newDelegate) {
            if (isDelegate) {
                delegate.mkDelegate(newDelegate);
            } else {
//                System.out.println("Making " + this + " a delegate to " + newDelegate);
                isDelegate = true;
                delegate = newDelegate;
            }
        }

        public void forceType(JavaTypeInstance rawJavaType, boolean ignoreLock) {
            if (!ignoreLock && isLocked()) return;
            if (isDelegate && delegate.isLocked() && !ignoreLock) {
                isDelegate = false;
            }
            if (isDelegate) {
                delegate.forceType(rawJavaType, ignoreLock);
            } else {
                this.type = rawJavaType;
            }
        }

        public void markClashState(ClashState newClashState) {
            if (this.clashState != null) {
                this.clashState = newClashState;
                return;
            }
            if (isDelegate) {
                delegate.markClashState(newClashState);
            }
        }

        public String toString() {
            if (isDelegate) {
                return "#" + id + " -> " + delegate.toString();
            } else {
                return "#" + id + " " + type.toString();
            }
        }

        public boolean isLocked() {
            return locked;
        }
    }

    private IJTInternal value;

    public static final InferredJavaType IGNORE = new InferredJavaType();

    public InferredJavaType() {
        value = new IJTInternal(RawJavaType.VOID, Source.UNKNOWN, false);
    }

    public InferredJavaType(JavaTypeInstance type, Source source) {
        value = new IJTInternal(type, source, false);
    }

    public InferredJavaType(JavaTypeInstance type, Source source, boolean locked) {
        value = new IJTInternal(type, source, locked);
    }

    public Source getSource() {
        return value.getSource();
    }

    private void mergeGenericInfo(JavaGenericRefTypeInstance otherTypeInstance) {
        if (this.value.isLocked()) return;
        JavaGenericRefTypeInstance thisType = (JavaGenericRefTypeInstance) this.value.getJavaTypeInstance();
        if (!thisType.hasUnbound()) return;
        ClassFile degenerifiedThisClassFile = thisType.getDeGenerifiedType().getClassFile();
        if (degenerifiedThisClassFile == null) {
            return;
        }
        JavaTypeInstance boundThisType = degenerifiedThisClassFile.getBindingSupers().getBoundAssignable(thisType, otherTypeInstance);
        if (!boundThisType.equals(thisType)) {
            mkDelegate(this.value, new IJTInternal(boundThisType, Source.GENERICCALL, true));
        }
    }

    public void noteUseAs(JavaTypeInstance type) {
        if (value.getClashState() == ClashState.Clash) {
            BindingSuperContainer bindingSuperContainer = getJavaTypeInstance().getBindingSupers();
            if (bindingSuperContainer.containsBase(type.getDeGenerifiedType())) {
                value.forceType(type, false);
                value.markClashState(ClashState.Resolved);
            }
        }
    }

    private boolean checkBaseCompatibility(JavaTypeInstance otherType) {
        JavaTypeInstance thisStripped = getJavaTypeInstance().getDeGenerifiedType();
        JavaTypeInstance otherStripped = otherType.getDeGenerifiedType();
        if (thisStripped.equals(otherStripped)) return true;

        BindingSuperContainer otherSupers = otherType.getBindingSupers();
        if (otherSupers == null) {
            // We're stuck.  Can't do this, best effort!
            return true;
        } else {
            return otherSupers.containsBase(thisStripped);
        }
    }

    private CastAction chainFrom(InferredJavaType other) {
        if (this == other) return CastAction.None;

        JavaTypeInstance thisTypeInstance = this.value.getJavaTypeInstance();
        JavaTypeInstance otherTypeInstance = other.value.getJavaTypeInstance();

        /*
         * Can't chain if this isn't a simple, or a supertype of other.
         */
        if (thisTypeInstance.isComplexType() && otherTypeInstance.isComplexType()) {
            if (!checkBaseCompatibility(other.getJavaTypeInstance())) {
                // Break the chain here, mark this delegate as bad.
                this.value = new IJTInternal(new IJTInternal(other.getJavaTypeInstance(), other.getSource(), true), false, ClashState.Clash);
                // this.value.markTypeClash();
                return CastAction.None;
            } else if (this.value.getClashState() == ClashState.Resolved) {
                return CastAction.None;
            }
        }


        if (otherTypeInstance instanceof JavaGenericRefTypeInstance) {
            if (thisTypeInstance instanceof JavaGenericRefTypeInstance) {
                other.mergeGenericInfo((JavaGenericRefTypeInstance) thisTypeInstance);
            }
        }

        mkDelegate(this.value, other.value);
        if (!other.value.isLocked()) {
            this.value = other.value; // new IJTDelegate(other);
        }
        return CastAction.None;
    }

    private static void mkDelegate(IJTInternal a, IJTInternal b) {
        if (a.getFinalId() != b.getFinalId()) {
            a.mkDelegate(b);
        }
    }

    /*
    * v0 [t1<-] = true [t1]
    * v1 [t2<-] = true [t2]
    * v3 [t2<-] = v1;
    * v3 = v0;
    *
    */
    private CastAction chainIntegralTypes(InferredJavaType other) {
        if (this == other) return CastAction.None;
        int pri = getRawType().compareTypePriorityTo(other.getRawType());
        if (pri >= 0) {
            if (other.value.isLocked()) {
                if (pri > 0) {
                    return CastAction.InsertExplicit;
                } else {
                    return CastAction.None;
                }
            }
            mkDelegate(other.value, this.value);
        } else {
            if (this.value.isLocked()) {
                return CastAction.InsertExplicit;
            }
            mkDelegate(this.value, other.value);
            this.value = other.value;
        }
        return CastAction.None;
    }

    public static void compareAsWithoutCasting(InferredJavaType a, InferredJavaType b) {
        if (a == InferredJavaType.IGNORE) return;
        if (b == InferredJavaType.IGNORE) return;

        RawJavaType art = a.getRawType();
        RawJavaType brt = b.getRawType();
        if (art.getStackType() != StackType.INT ||
                brt.getStackType() != StackType.INT) return;

        InferredJavaType litType = null;
        InferredJavaType betterType = null;
        Expression litExp = null;
        switch (Troolean.get(
                a.getSource() == InferredJavaType.Source.LITERAL,
                b.getSource() == InferredJavaType.Source.LITERAL)) {
            case NEITHER:
                return;
            case FIRST:
                litType = a;
                betterType = b;
                break;
            case SECOND:
                litType = b;
                betterType = a;
                break;
            case BOTH:
                return; // for now.
        }
        // If betterType is wider than litType, just use it.  If it's NARROWER than litType,
        // we need to see if litType can support it. (i.e. 34343 can't be cast to a char).
        //
        // ACTUALLY, we can cheat here!  this is because in java we CAN compare an int to a char...
        litType.chainFrom(betterType);
    }


    /*
     * This is being explicitly casted by (eg) i2c.  We need to cut the chain.
     */
    public void useAsWithCast(RawJavaType otherRaw) {
        if (this == IGNORE) return;

        this.value = new IJTInternal(otherRaw, Source.OPERATION, true);
    }

    public void useInArithOp(InferredJavaType other, boolean forbidBool) {
        if (this == IGNORE) return;
        if (other == IGNORE) return;
        RawJavaType thisRaw = getRawType();
        RawJavaType otherRaw = other.getRawType();
        if (thisRaw.getStackType() != otherRaw.getStackType()) {
            // TODO : Might have to be some casting going on.
            // This would never happen in raw bytecode, as everything would have correct intermediate
            // casts - but we might have stripped these now....
            return;
        }
        if (thisRaw.getStackType() == StackType.INT) {
            // Find the 'least' specific, tie to that.
            // (We've probably got an arithop between an inferred boolean and a real int... )
            int cmp = thisRaw.compareTypePriorityTo(otherRaw);
            if (cmp < 0) {
                this.value.forceType(otherRaw, false);
            } else if (cmp == 0) {
                if (thisRaw == RawJavaType.BOOLEAN && forbidBool) {
                    this.value.forceType(RawJavaType.INT, false);
                }
            }
        }
    }

    public static void useInArithOp(InferredJavaType lhs, InferredJavaType rhs, ArithOp op) {
        boolean forbidBool = true;
        if (op == ArithOp.OR || op == ArithOp.AND) forbidBool = false;
        lhs.useInArithOp(rhs, forbidBool);
        rhs.useInArithOp(lhs, forbidBool);
    }

    /*
     * This is being used as an argument to a known typed function.  Maybe we can infer some type information.
     *
     * Todo : this needs much more structure.
     */
    public void useAsWithoutCasting(JavaTypeInstance otherTypeInstance) {
        if (this == IGNORE) return;

        /* If value is something that can legitimately be forced /DOWN/
         * (i.e. from int to char) then we should push it down.
         *
         * If it's being upscaled, we don't affect it.
         */
        JavaTypeInstance thisTypeInstance = getJavaTypeInstance();
        if (thisTypeInstance instanceof RawJavaType &&
                otherTypeInstance instanceof RawJavaType) {
            RawJavaType otherRaw = otherTypeInstance.getRawTypeOfSimpleType();
            RawJavaType thisRaw = getRawType();
            if (thisRaw.getStackType() != otherRaw.getStackType()) return;
            if (thisRaw.getStackType() == StackType.INT) {
                // Find the 'least' specific, tie to that.
                int cmp = thisRaw.compareTypePriorityTo(otherRaw);
                if (cmp > 0) {
                    this.value.forceType(otherRaw, false);
                } else if (cmp < 0) {
                    // This special case is because we aggressively try to treat 0/1 as boolean,
                    // which comes back to bite us if they're used as arguments to a wider typed function
                    // we can't see foo((int)false))!
                    if (thisRaw == RawJavaType.BOOLEAN) {
                        this.value.forceType(otherRaw, false);
                    }
                }
            }
            return;
        } else if (thisTypeInstance instanceof JavaArrayTypeInstance &&
                otherTypeInstance instanceof JavaArrayTypeInstance) {
            JavaArrayTypeInstance thisArrayTypeInstance = (JavaArrayTypeInstance) thisTypeInstance;
            JavaArrayTypeInstance otherArrayTypeInstance = (JavaArrayTypeInstance) otherTypeInstance;
            if (thisArrayTypeInstance.getNumArrayDimensions() != otherArrayTypeInstance.getNumArrayDimensions()) return;

            JavaTypeInstance thisStripped = thisArrayTypeInstance.getArrayStrippedType().getDeGenerifiedType();
            JavaTypeInstance otherStripped = otherArrayTypeInstance.getArrayStrippedType().getDeGenerifiedType();

            if (thisStripped instanceof JavaRefTypeInstance &&
                    otherStripped instanceof JavaRefTypeInstance) {
                JavaRefTypeInstance thisRef = (JavaRefTypeInstance) thisStripped;
                JavaRefTypeInstance otherRef = (JavaRefTypeInstance) otherStripped;
                BindingSuperContainer bindingSuperContainer = thisRef.getBindingSupers();
                if (bindingSuperContainer == null) { // HACK.  It's a hardcoded type.
                    if (otherRef == TypeConstants.OBJECT) {
                        this.value.forceType(otherTypeInstance, false);
                    }
                } else {
                    if (bindingSuperContainer.containsBase(otherRef)) {
                        this.value.forceType(otherTypeInstance, false);
                    }
                }
            }
        }
    }

    public void deGenerify(JavaTypeInstance other) {
        JavaTypeInstance typeInstanceThis = getJavaTypeInstance().getDeGenerifiedType();
        JavaTypeInstance typeInstanceOther = other;
        if (!typeInstanceOther.equals(typeInstanceThis)) {
            if (!("java/lang/Object".equals(typeInstanceThis.getRawName()))) {
                throw new ConfusedCFRException("Incompatible types : " + typeInstanceThis.getClass() + "[" + typeInstanceThis + "] / " + typeInstanceOther.getClass() + "[" + typeInstanceOther + "]");
            }
        }
        value.forceType(other, true);
    }

    /* We've got some type info about this type already, but we're assigning from other.
     * so, if we can, let's narrow this type, or chain it from
     */
    public CastAction chain(InferredJavaType other) {
        if (this == IGNORE) return CastAction.None;
        if (other == IGNORE) return CastAction.None;

        if (other.getRawType() == RawJavaType.VOID) {
            return CastAction.None;
        }

        RawJavaType thisRaw = value.getRawType();
        RawJavaType otherRaw = other.getRawType();

        if (thisRaw == RawJavaType.VOID) {
            return chainFrom(other);
        }

        if (thisRaw.getStackType() != otherRaw.getStackType()) {
            // throw new ConfusedCFRException("Can't tighten from " + thisRaw + " to " + otherRaw);
            return CastAction.InsertExplicit;
        }
        if (thisRaw == otherRaw && thisRaw.getStackType() != StackType.INT) {
            return chainFrom(other);
        }
        if (thisRaw == RawJavaType.NULL && (otherRaw == RawJavaType.NULL || otherRaw == RawJavaType.REF)) {
            return chainFrom(other);
        }
        if (thisRaw.getStackType() == StackType.INT) {
            if (otherRaw.getStackType() != StackType.INT) {
                throw new IllegalStateException();
            }
            return chainIntegralTypes(other);
        }
        throw new ConfusedCFRException("Don't know how to tighten from " + thisRaw + " to " + otherRaw);
    }

    public RawJavaType getRawType() {
//        System.out.println(super.toString());
        return value.getRawType();
    }

    public String getCastString() {
        return value.getJavaTypeInstance().toString();
    }

    public JavaTypeInstance getJavaTypeInstance() {
        return value.getJavaTypeInstance();
    }

    @Override
    public boolean equals(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int hashCode() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
        return (value.getClashState() == ClashState.Clash) ? " /* !! */ " : "";
        //return "[" + (value.isMarkedBad() ? "!!" : "") + value.toString() + "]";
    }
}
