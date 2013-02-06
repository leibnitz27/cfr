package org.benf.cfr.reader.entities;

import org.benf.cfr.reader.bytecode.analysis.types.*;
import org.benf.cfr.reader.util.*;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.List;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 10/08/2012
 * <p/>
 * Reflection based version of generic hunter.
 */
public class GenericInfoSource {
    private final ConstantPool cp;

    public GenericInfoSource(ConstantPool cp) {
        this.cp = cp;
    }

    /* Someone is calling
     *
     * clazzInstance.methName ( methodPrototype ).
     *
     * We need to actually find THAT method in that class, and see if it has a generic signature attached to it, which might
     * give us more detail about the return type.
     *
     * eg
     *
     * Map<String, Integer> x;
     * y = x.keySet(); <-- here, we'd think y is a Set, because that's all the info the LOCAL constpool has.
     * But it's actually Set<String>
     *
     * (If the local constpool carried all of this, we wouldn't have to go through this rigamarole :( )
     */
    public JavaTypeInstance getGenericTypeInfo(JavaTypeInstance bestGuess, JavaGenericRefTypeInstance clazzInfo, final String methName, MethodPrototype methodPrototype) {
        String rawClassName = clazzInfo.getClassName();
        rawClassName = ClassNameUtils.convert(rawClassName);
        Class clazz = null;
        try {
            clazz = Class.forName(rawClassName, false, this.getClass().getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new ConfusedCFRException(e);
        }
//        System.out.println("Loaded class name " + clazz.getName());
        // these are the known instances of the type variables.  We will replace them, if possible.
        List<JavaTypeInstance> argParameterTypes = methodPrototype.getArgs();
        // We don't 'properly' use type lookup - that would involve loading all dependant types, so for now I'm trying to
        // manually match.
        List<java.lang.reflect.Method> methods2 = ListFactory.newList(clazz.getMethods());
        methods2 = Functional.filter(methods2, new Predicate<java.lang.reflect.Method>() {
            @Override
            public boolean test(Method in) {
                return in.getName().equals(methName);
            }
        });
        // Now find the one which matches our parameterTypes.
        List<java.lang.reflect.Method> methods = Functional.filter(methods2, new MatchesParameterTypes(argParameterTypes));
        if (methods.size() != 1) {
            System.out.println("Parameter types " + argParameterTypes);
            for (java.lang.reflect.Method method : methods2) {
                System.out.println(method);
            }
            throw new RuntimeException("Gen type." + methods.size());
        }

        // These are the type variables.  We match these against the type variables we have in clazzInfo, to
        // generate a 'better' version of
        TypeVariable[] typeVariables = clazz.getTypeParameters();
        GenericLookupMap genericLookupMap = new GenericLookupMap(typeVariables, clazzInfo.getGenericTypes());

        Method method = methods.get(0);
        // System.out.println(method);
        Type returnType = method.getGenericReturnType();

        JavaTypeInstance genericTypeInstance = convertJavaTypeToCFRType(returnType, cp, genericLookupMap);
        return genericTypeInstance;
    }

    private JavaTypeInstance convertJavaParameterizedTypeToCFRType(ParameterizedType reflectType, ConstantPool cp, GenericLookupMap genericLookupMap) {
        Type[] args = reflectType.getActualTypeArguments();
        Type thisType = reflectType.getRawType();
        List<JavaTypeInstance> convArgs = ListFactory.newList();
        for (Type arg : args) {
            convArgs.add(convertJavaTypeToCFRType(arg, cp, genericLookupMap));
        }
        JavaTypeInstance thisType2 = convertJavaTypeToCFRType(thisType, cp, genericLookupMap);
        JavaTypeInstance convThisType = new JavaGenericRefTypeInstance(thisType2, convArgs, cp);
        return convThisType;
    }

    private JavaTypeInstance convertJavaTypeVariableToCFRType(TypeVariable typeVariable, ConstantPool cp, GenericLookupMap genericLookupMap) {
        JavaTypeInstance val = genericLookupMap.getClass(typeVariable);
        return val;
    }

    private JavaTypeInstance convertJavaClassTypeToCFRType(Class clazz, ConstantPool cp) {
        if (clazz.isPrimitive()) {
            return RawJavaType.getByName(clazz.getName());
        } else {
            return new JavaRefTypeInstance(clazz.getName(), cp);
        }
    }

    private JavaTypeInstance convertJavaTypeToCFRType(Type reflectType, ConstantPool cp, GenericLookupMap genericLookupMap) {
        if (reflectType instanceof ParameterizedType) {
            return convertJavaParameterizedTypeToCFRType((ParameterizedType) reflectType, cp, genericLookupMap);
        } else if (reflectType instanceof TypeVariable) {
            return convertJavaTypeVariableToCFRType((TypeVariable) reflectType, cp, genericLookupMap);
        } else if (reflectType instanceof Class) {
            return convertJavaClassTypeToCFRType((Class) reflectType, cp);
        } else {
            throw new RuntimeException("Don't speak " + reflectType.getClass());
        }
    }

    private static final class MatchesParameterTypes implements Predicate<java.lang.reflect.Method> {
        private final List<JavaTypeInstance> cfrTypes;

        private MatchesParameterTypes(List<JavaTypeInstance> cfrTypes) {
            this.cfrTypes = cfrTypes;
        }

        @Override
        public boolean test(Method in) {
            Class paramclazzes[] = in.getParameterTypes();
            if (paramclazzes.length != cfrTypes.size()) return false;
            for (int idx = 0; idx < paramclazzes.length; ++idx) {
                Class paramclazz = paramclazzes[idx];
                JavaTypeInstance javaTypeInstance = cfrTypes.get(idx);
                String nameParam = paramclazz.getName().replace(".", "/");
                String nameArg = javaTypeInstance.getRawName();
                if (!(nameArg.equals(nameParam))) return false;
            }
            return true;
        }
    }

    private static final class GenericLookupMap {
        private final Map<TypeVariable, JavaTypeInstance> map = MapFactory.newMap();

        public GenericLookupMap(TypeVariable[] typeVariables, List<JavaTypeInstance> parameterTypes) {
            if (typeVariables.length != parameterTypes.size()) {
                throw new ConfusedCFRException("Type parameter mismatch " + typeVariables.length + " vs " + parameterTypes.size());
            }
            for (int x = 0; x < typeVariables.length; ++x) {
                map.put(typeVariables[x], parameterTypes.get(x));
            }
        }

        public JavaTypeInstance getClass(TypeVariable typeVariable) {
            JavaTypeInstance res = map.get(typeVariable);
            if (res == null) {
                throw new ConfusedCFRException("Null type match " + typeVariable);
            }
            return res;
        }
    }
}
