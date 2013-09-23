package org.benf.cfr.reader.bytecode.analysis.types;

import org.benf.cfr.reader.util.ConfusedCFRException;
import org.benf.cfr.reader.util.MapFactory;
import org.benf.cfr.reader.util.SetFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 25/02/2013
 * Time: 10:09
 */
public class GenericTypeBinder {
    private final Map<String, JavaTypeInstance> nameToBoundType;

    private GenericTypeBinder(Map<String, JavaTypeInstance> nameToBoundType) {
        this.nameToBoundType = nameToBoundType;
    }

    // TODO : This seems wrong.
    public static GenericTypeBinder createEmpty() {
        return new GenericTypeBinder(MapFactory.<String, JavaTypeInstance>newMap());
    }

    public static GenericTypeBinder bind(List<FormalTypeParameter> methodFormalTypeParameters,
                                         ClassSignature classSignature, List<JavaTypeInstance> args,
                                         JavaGenericRefTypeInstance boundInstance, List<JavaTypeInstance> boundArgs) {
        Map<String, JavaTypeInstance> nameToBoundType = MapFactory.newMap();

        if (boundInstance != null) {    // null for static.
            List<FormalTypeParameter> unboundParameters = classSignature.getFormalTypeParameters();
            List<JavaTypeInstance> boundParameters = boundInstance.getGenericTypes();

            if (boundParameters.size() != unboundParameters.size()) {
                // I suspect this will happen all the time, but on the face of it I can't see why it should
                // be valid right now.
                throw new UnsupportedOperationException();
            }

            for (int x = 0; x < boundParameters.size(); ++x) {
                nameToBoundType.put(unboundParameters.get(x).getName(), boundParameters.get(x));
            }
        }

        List<FormalTypeParameter> classFormalTypeParamters = classSignature.getFormalTypeParameters();
        // TODO: Pretty sure this is a tautology given the calling pattern.

        GenericTypeBinder res = new GenericTypeBinder(nameToBoundType);

        if ((methodFormalTypeParameters != null && !methodFormalTypeParameters.isEmpty()) ||
                (classFormalTypeParamters != null && !classFormalTypeParamters.isEmpty())) {
            if (args.size() != boundArgs.size())
                throw new IllegalArgumentException(); // should be verified before we get here!


            for (int x = 0; x < args.size(); ++x) {
                JavaTypeInstance unbound = args.get(x);
                JavaTypeInstance bound = boundArgs.get(x);
                if (unbound instanceof JavaGenericBaseInstance) {
                    JavaGenericBaseInstance unboundGeneric = (JavaGenericBaseInstance) unbound;
                    unboundGeneric.tryFindBinding(bound, res);
                }
            }
        }

        return res;
    }

    public static GenericTypeBinder buildIdentityBindings(JavaGenericRefTypeInstance unbound) {
        List<JavaTypeInstance> typeParameters = unbound.getGenericTypes();

        Map<String, JavaTypeInstance> unboundNames = MapFactory.newMap();
        for (int x = 0, len = typeParameters.size(); x < len; ++x) {
            JavaTypeInstance unboundParam = typeParameters.get(x);
            if (!(unboundParam instanceof JavaGenericPlaceholderTypeInstance)) {
                throw new ConfusedCFRException("Unbound parameter expected to be placeholder!");
            }
            unboundNames.put(unboundParam.getRawName(), unboundParam);
        }
        return new GenericTypeBinder(unboundNames);
    }

    public static GenericTypeBinder extractBindings(JavaGenericRefTypeInstance unbound, JavaGenericRefTypeInstance bound) {
        List<JavaTypeInstance> typeParameters = unbound.getGenericTypes();
        List<JavaTypeInstance> boundTypeParameters = bound.getGenericTypes();
        if (typeParameters.size() != boundTypeParameters.size())
            throw new IllegalStateException("Generic info mismatch");

        Map<String, JavaTypeInstance> boundNames = MapFactory.newMap();
        for (int x = 0, len = typeParameters.size(); x < len; ++x) {
            JavaTypeInstance unboundParam = typeParameters.get(x);
            JavaTypeInstance boundParam = boundTypeParameters.get(x);
            if (!(unboundParam instanceof JavaGenericPlaceholderTypeInstance)) {
                continue;
            }
            JavaGenericPlaceholderTypeInstance placeholder = (JavaGenericPlaceholderTypeInstance) unboundParam;
            boundNames.put(placeholder.getRawName(), boundParam);
        }
        return new GenericTypeBinder(boundNames);
    }

    public JavaTypeInstance getBindingFor(JavaTypeInstance maybeUnbound) {
        if (maybeUnbound instanceof JavaGenericPlaceholderTypeInstance) {
            JavaGenericPlaceholderTypeInstance placeholder = (JavaGenericPlaceholderTypeInstance) maybeUnbound;
            String name = placeholder.getRawName();
            JavaTypeInstance bound = nameToBoundType.get(name);
            if (bound != null) {
                return bound;
            }
        } else if (maybeUnbound instanceof JavaGenericRefTypeInstance) {
            return ((JavaGenericRefTypeInstance) maybeUnbound).getBoundInstance(this);
        }
        return maybeUnbound;
    }

    private static boolean isBetterBinding(JavaTypeInstance isBetter, JavaTypeInstance than) {
        if (than == null) return true;
        if (isBetter instanceof JavaGenericPlaceholderTypeInstance) return false;
        return true;
    }

    public void suggestBindingFor(String name, JavaTypeInstance binding) {
        JavaTypeInstance alreadyBound = nameToBoundType.get(name);
        if (isBetterBinding(binding, alreadyBound)) {
            nameToBoundType.put(name, binding);
        }
    }

    public GenericTypeBinder mergeWith(GenericTypeBinder other, boolean mergeToCommonClass) {
        Set<String> keys = SetFactory.newSet(nameToBoundType.keySet());
        keys.addAll(other.nameToBoundType.keySet());
        Map<String, JavaTypeInstance> res = MapFactory.newMap();
        for (String key : keys) {
            JavaTypeInstance t1 = nameToBoundType.get(key);
            JavaTypeInstance t2 = other.nameToBoundType.get(key);
            if (t1 == null) {
                res.put(key, t2);
                continue;
            }
            if (t2 == null) {
                res.put(key, t1);
                continue;
            }
            /*
             * Ok. Try to merge. Find highest common base class.
             * If completely incompatible, return null.
             */
            if (mergeToCommonClass) {
                if (t1.implicitlyCastsTo(t2)) {
                    res.put(key, t2);
                    continue;
                }
                if (t2.implicitlyCastsTo(t1)) {
                    res.put(key, t1);
                }
            }
            return null;
        }
        return new GenericTypeBinder(res);
    }
}
