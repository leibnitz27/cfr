package org.benf.cfr.reader.bytecode.analysis.types.discovery;

import org.benf.cfr.reader.bytecode.analysis.parse.expression.ArithOp;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.RawJavaType;
import org.benf.cfr.reader.bytecode.analysis.types.StackType;
import org.benf.cfr.reader.util.ConfusedCFRException;

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
        OPERATION,
        EXPRESSION,
        INSTRUCTION, // Instr returns type which guarantees this (eg arraylength returns int).
        GENERICCALL
    }


    private static int global_id = 0;

    private static class IJTInternal {

        private boolean isDelegate = false;
        // When not delegating
        private JavaTypeInstance type;
        private final Source source;
        private final int id;
        // When delegating
        private IJTInternal delegate;

        private IJTInternal(JavaTypeInstance type, Source source) {
            this.type = type;
            this.source = source;
            this.id = global_id++;
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

        public int getFinalId() {
            if (isDelegate) {
                return delegate.getFinalId();
            } else {
                return id;
            }
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

        public void force(RawJavaType rawJavaType) {
            if (isDelegate) {
                delegate.force(rawJavaType);
            } else {
                this.type = rawJavaType;
            }
        }

        public void forceGeneric(JavaTypeInstance rawJavaType) {
            if (isDelegate) {
                delegate.forceGeneric(rawJavaType);
            } else {
                this.type = rawJavaType;
            }
        }

        public String toString() {
            if (isDelegate) {
                return "#" + id + " -> " + delegate.toString();
            } else {
                return "#" + id + " " + type.toString();
            }
        }
    }

    private IJTInternal value;

    public static final InferredJavaType IGNORE = new InferredJavaType();

    public InferredJavaType() {
        value = new IJTInternal(RawJavaType.VOID, Source.UNKNOWN);
    }

    public InferredJavaType(JavaTypeInstance type, Source source) {
        value = new IJTInternal(type, source);
    }

    private void chainFrom(InferredJavaType other) {
        if (this == other) return;
        mkDelegate(this.value, other.value);
        this.value = other.value; // new IJTDelegate(other);
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
    */
    private void chainIntegralTypes(InferredJavaType other) {
        if (this == other) return;
        int pri = getRawType().compareTypePriorityTo(other.getRawType());
        if (pri >= 0) {
            mkDelegate(other.value, this.value);
        } else {
            mkDelegate(this.value, other.value);
            this.value = other.value;
        }
    }

    /*
     * This is being explicitly casted by (eg) i2c.  We need to cut the chain.
     */
    public void useAsWithCast(RawJavaType otherRaw) {
        if (this == IGNORE) return;

        this.value = new IJTInternal(otherRaw, Source.OPERATION);
    }

    public static void compareAsWithoutCasting(InferredJavaType a, InferredJavaType b) {
        if (a == IGNORE) return;
        if (b == IGNORE) return;

        RawJavaType art = a.getRawType();
        RawJavaType brt = b.getRawType();
        if (art.getStackType() != StackType.INT ||
                brt.getStackType() != StackType.INT) return;

        InferredJavaType takeFromType = null;
        InferredJavaType pushToType = null;
        if (art == RawJavaType.INT) {
            takeFromType = b;
            pushToType = a;
        } else if (brt == RawJavaType.INT) {
            takeFromType = a;
            pushToType = b;
        } else {
            return;
        }
        pushToType.chainIntegralTypes(takeFromType);
//        pushToType.useAsWithoutCasting(takeFromType);
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
                this.value.force(otherRaw);
            } else if (cmp == 0) {
                if (thisRaw == RawJavaType.BOOLEAN && forbidBool) {
                    this.value.force(RawJavaType.INT);
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
     */
    public void useAsWithoutCasting(RawJavaType otherRaw) {
        if (this == IGNORE) return;

        /* If value is something that can legitimately be forced /DOWN/
         * (i.e. from int to char) then we should push it down.
         *
         * If it's being upscaled, we don't affect it.
         */
        RawJavaType thisRaw = getRawType();
        if (thisRaw.getStackType() != otherRaw.getStackType()) return;
        if (thisRaw.getStackType() == StackType.INT) {
            // Find the 'least' specific, tie to that.
            int cmp = thisRaw.compareTypePriorityTo(otherRaw);
            if (cmp < 0) {
                this.value.force(otherRaw);
            } else if (cmp > 0) {
                int x = 3;
//                other.value.force(thisRaw);
            }
        }
    }

    public void generify(JavaTypeInstance other) {
        JavaTypeInstance typeInstanceThis = getJavaTypeInstance();
        JavaTypeInstance typeInstanceOther = other.getDeGenerifiedType();
        if (!typeInstanceOther.equals(typeInstanceThis)) {
            if (!("java/lang/Object".equals(typeInstanceThis.getRawName()))) {
                throw new ConfusedCFRException("Incompatible types : " + typeInstanceThis.getClass() + "[" + typeInstanceThis + "] / " + typeInstanceOther.getClass() + "[" + typeInstanceOther + "]");
            }
        }
        value.forceGeneric(other);
    }

    /* We've got some type info about this type already, but we're assigning from other.
     * so, if we can, let's narrow this type, or chain it from
     */
    public void chain(InferredJavaType other) {
        if (this == IGNORE) return;
        if (other == IGNORE) return;

        if (other.getRawType() == RawJavaType.VOID) {
            return;
        }

        RawJavaType thisRaw = value.getRawType();
        RawJavaType otherRaw = other.getRawType();

        if (thisRaw == RawJavaType.VOID) {
            chainFrom(other);
            return;
        }

        if (thisRaw.getStackType() != otherRaw.getStackType()) {
            // throw new ConfusedCFRException("Can't tighten from " + thisRaw + " to " + otherRaw);
            return;
        }
        if (thisRaw == otherRaw && thisRaw.getStackType() != StackType.INT) {
            chainFrom(other);
            return;
        }
        if (thisRaw.getStackType() == StackType.INT) {
            if (otherRaw.getStackType() != StackType.INT) {
                throw new IllegalStateException();
            }
            chainIntegralTypes(other);
            return;
        }
        throw new ConfusedCFRException("Don't know how to tighten from " + thisRaw + " to " + otherRaw);
    }

    public RawJavaType getRawType() {
//        System.out.println(super.toString());
        return value.getRawType();
    }

    public JavaTypeInstance getJavaTypeInstance() {
        return value.getJavaTypeInstance();
    }

    @Override
    public String toString() {
        return "";
        // return "[" + value.toString() + "]";
    }
}
