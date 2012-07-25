package org.benf.cfr.reader.bytecode.analysis.types.discovery;

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
        INSTRUCTION // Instr returns type which guarantees this (eg arraylength returns int).
    }

    private interface IJTInternal {
        RawJavaType getRawType();

        boolean isChained();

        void chain(InferredJavaType chainTo);

        void force(RawJavaType rawJavaType);
    }

    private class IJTLocal implements IJTInternal {
        private JavaTypeInstance type;
        private final Source source;

        private IJTLocal(JavaTypeInstance type, Source source) {
            this.type = type;
            this.source = source;
        }

        @Override
        public RawJavaType getRawType() {
            // Think this might bite me later?
            return type.getRawTypeOfSimpleType();
        }

        @Override
        public boolean isChained() {
            return false;
        }

        @Override
        public void chain(InferredJavaType chainTo) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void force(RawJavaType rawJavaType) {
            /*
             * TODO : verify.
             */
            this.type = rawJavaType;
        }

        @Override
        public String toString() {
            return type.toString();
        }
    }

    private class IJTDelegate implements IJTInternal {
        private final InferredJavaType delegate;

        private IJTDelegate(InferredJavaType delegate) {
            this.delegate = delegate;
        }

        @Override
        public RawJavaType getRawType() {
            return delegate.getRawType();
        }

        @Override
        public boolean isChained() {
            return true;
        }

        @Override
        public void chain(InferredJavaType chainTo) {
            delegate.chain(chainTo);
        }

        @Override
        public void force(RawJavaType rawJavaType) {
            delegate.useAsWithoutCasting(rawJavaType);
        }

        @Override
        public String toString() {
            return delegate.toString();
        }
    }

    private IJTInternal value;

    public static final InferredJavaType IGNORE = new InferredJavaType();

    public InferredJavaType() {
        value = new IJTLocal(RawJavaType.VOID, Source.UNKNOWN);
    }

    public InferredJavaType(JavaTypeInstance type, Source source) {
        value = new IJTLocal(type, source);
    }

    private void chainFrom(InferredJavaType other) {
        if (this == other) return;
        /*
         * Or if this would introduce a cycle.  This is madly inefficient - do it a more sensible way.
         */
        this.value = other.value; // new IJTDelegate(other);
    }

    /*
     * This is being explicitly casted by (eg) i2c.  We need to cut the chain.
     */
    public void useAsWithCast(RawJavaType otherRaw) {
        if (this == IGNORE) return;

        this.value = new IJTLocal(otherRaw, Source.OPERATION);
    }

    public static void compareAsWithoutCasting(InferredJavaType a, InferredJavaType b) {
        if (a == IGNORE) return;
        if (b == IGNORE) return;

        RawJavaType art = a.getRawType();
        RawJavaType brt = b.getRawType();
        if (art.getStackType() != StackType.INT ||
                brt.getStackType() != StackType.INT) return;

        RawJavaType takeFromType = null;
        InferredJavaType pushToType = null;
        if (art == RawJavaType.INT) {
            takeFromType = brt;
            pushToType = a;
        } else if (brt == RawJavaType.INT) {
            takeFromType = art;
            pushToType = b;
        } else {
            return;
        }
        pushToType.useAsWithoutCasting(takeFromType);
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
        switch (thisRaw) {
            case INT:
                switch (otherRaw) {
                    case INT:
                        return;
                    case SHORT:
                    case BOOLEAN:
                    case BYTE:
                    case CHAR:
                        value.force(otherRaw);
                        return;
                    default:
                        throw new ConfusedCFRException("This " + thisRaw + " other " + otherRaw);
                }
            case BOOLEAN:
                switch (otherRaw) {
                    case INT:
                    case SHORT:
                    case BOOLEAN:
                    case BYTE:
                    case CHAR:
                        return;
                    default:
                        throw new IllegalStateException();
                }
            default:
                break;
        }
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
        if (value.isChained()) {
            value.chain(other);
            return;
        }

        RawJavaType thisRaw = value.getRawType();
        if (thisRaw == RawJavaType.VOID) {
            chainFrom(other);
            return;
        }
        RawJavaType otherRaw = other.getRawType();

        if (thisRaw.getStackType() != otherRaw.getStackType()) {
            return;
//            throw new ConfusedCFRException("Can't tighten from " + thisRaw + " to " + otherRaw);
        }
        if (thisRaw == otherRaw) {
            chainFrom(other);
            return;
        }
        switch (thisRaw) {
            case INT:
                switch (otherRaw) {
                    case INT:
                    case SHORT:
                    case BOOLEAN:
                    case BYTE:
                    case CHAR:
                        chainFrom(other);
                        return;
                    default:
                        throw new IllegalStateException();
                }
            case CHAR:
            case BOOLEAN:
            case BYTE:
            case SHORT:
                switch (otherRaw) {
                    case INT:
                    case SHORT:
                    case BOOLEAN:
                    case BYTE:
                    case CHAR:
                        return;
                    default:
                        throw new IllegalStateException();
                }
            default:
                break;
        }
        throw new ConfusedCFRException("Don't know how to tighten from " + thisRaw + " to " + otherRaw);
    }

    public RawJavaType getRawType() {
//        System.out.println(super.toString());
        return value.getRawType();
    }

    @Override
    public String toString() {
        return "";
        //return "(" + value + ")";
    }
}
