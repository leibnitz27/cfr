package org.benf.cfr.reader.bytecode.analysis.types.discovery;

import org.benf.cfr.reader.bytecode.analysis.types.JavaType;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.util.ConfusedCFRException;
import org.benf.cfr.reader.util.MapFactory;
import org.benf.cfr.reader.util.functors.UnaryFunction;

import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 13/07/2012
 * Time: 18:35
 */
public class KnownJavaType {
    private final JavaTypeInstance javaType;
    private final boolean isKnown;

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

    private KnownJavaType(JavaTypeInstance javaType, boolean known) {
        this.javaType = javaType;
        this.isKnown = known;
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
        if (!a.javaType.equals(b.javaType)) {
            throw new ConfusedCFRException("Combining java types : " + a + " and " + b);
        }
        if (a.isKnown) return a;
        return b;
    }

    @Override
    public String toString() {
        return "JavaType [" + javaType + "] sure? " + isKnown;
    }
}
