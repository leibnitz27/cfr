package org.benf.cfr.reader.bytecode.analysis.types.discovery;

import org.benf.cfr.reader.bytecode.analysis.types.JavaType;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.StackType;
import org.benf.cfr.reader.util.ConfusedCFRException;
import org.benf.cfr.reader.util.MapFactory;
import org.benf.cfr.reader.util.functors.UnaryFunction;

import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 13/07/2012
 * Time: 18:35
 * <p/>
 * Type promotion info:
 * <p/>
 * http://java.sun.com/docs/books/jls/third_edition/html/expressions.html#15.25
 */
public class KnownJavaType {
    private final JavaTypeInstance javaType;
    private final boolean isSure;

    private final static Map<JavaTypeInstance, KnownJavaType> knownTypes = MapFactory.newLazyMap(new UnaryFunction<JavaTypeInstance, KnownJavaType>() {
        @Override
        public KnownJavaType invoke(JavaTypeInstance arg) {
            return new KnownJavaType(arg, true);
        }
    });
    private final static Map<JavaTypeInstance, KnownJavaType> unknownTypes = MapFactory.newLazyMap(new UnaryFunction<JavaTypeInstance, KnownJavaType>() {
        @Override
        public KnownJavaType invoke(JavaTypeInstance arg) {
            return new KnownJavaType(arg, false);
        }
    });
    private final static KnownJavaType UNKNOWN = new KnownJavaType(null, false);

    private KnownJavaType(JavaTypeInstance javaType, boolean sure) {
        this.javaType = javaType;
        this.isSure = sure;
    }

    public static KnownJavaType getJavaType(JavaTypeInstance javaType, boolean known) {
        if (javaType == null) throw new ConfusedCFRException("Null JavaTypeInstance!");
        if (known) {
            return knownTypes.get(javaType);
        } else {
            return unknownTypes.get(javaType);
        }
    }

    public static KnownJavaType getKnownJavaType(JavaTypeInstance javaType) {
        return getJavaType(javaType, true);
    }

    public static KnownJavaType getUnknownJavaType(JavaTypeInstance javaType) {
        return getJavaType(javaType, false);
    }

    /* We don't know what the java type is, but we know what the stack type is -
     * map this to an unsure javatype.
     */
    public static KnownJavaType getUnknownStackType(StackType stackType) {
        JavaType javaType = JavaType.getJavaTypeForStackType(stackType);
        return getJavaType(javaType, false);
    }

    public static KnownJavaType getUnknown() {
        return UNKNOWN;
    }

    /*
     * This is all very vague right now.
     */
    public static KnownJavaType eitherOf(KnownJavaType a, KnownJavaType b) {
        if (a == UNKNOWN) return b;
        if (b == UNKNOWN) return a;
        if (a.javaType == JavaType.NULL) return b;
        if (b.javaType == JavaType.NULL) return a;
        if (a.javaType.equals(b.javaType)) {
            if (a.isSure) return a;
            return b;
        }
        StackType sa = a.javaType.getStackType();
        StackType sb = b.javaType.getStackType();
        if (sa == sb) {
            if (a.isSure) return a;
            return b;
        }
        /* Else we don't know .... but we can combine as per the spec. */
        // This could probably be done a lot nicer with some kind of precedence thingummy.
        switch (sa) {
            case INT:
                switch (sb) {
                    case FLOAT:
                    case DOUBLE:
                        return b;
                    default:
                        throw new ConfusedCFRException("Can't combine " + a + " / " + b);
                }
            case FLOAT:
                switch (sb) {
                    case INT:
                        return a;
                    case DOUBLE:
                        return b;
                    default:
                        throw new ConfusedCFRException("Can't combine " + a + " / " + b);
                }
            case DOUBLE:
                switch (sb) {
                    case INT:
                    case FLOAT:
                        return a;
                    default:
                        throw new ConfusedCFRException("Can't combine " + a + " / " + b);
                }
            default:
                throw new ConfusedCFRException("Can't combine " + a + " / " + b);
        }
    }

    @Override
    public String toString() {
        return "JavaType [" + javaType + "] sure? " + isSure;
    }
}
