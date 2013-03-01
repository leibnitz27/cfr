package org.benf.cfr.reader.bytecode.analysis.types;

import org.benf.cfr.reader.util.MapFactory;

import java.util.List;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 25/02/2013
 * Time: 10:09
 */
public class GenericTypeBinder {
    private final Map<String, JavaTypeInstance> nameToBoundType = MapFactory.newMap();

    public GenericTypeBinder() {
    }

    public GenericTypeBinder bind(List<FormalTypeParameter> methodFormalTypeParameters,
                                  ClassSignature classSignature, List<JavaTypeInstance> args,
                                  JavaGenericRefTypeInstance boundInstance, List<JavaTypeInstance> boundArgs) {

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
        if ((methodFormalTypeParameters != null && !methodFormalTypeParameters.isEmpty()) ||
                (classFormalTypeParamters != null && !classFormalTypeParamters.isEmpty())) {
            if (args.size() != boundArgs.size())
                throw new IllegalArgumentException(); // should be verified before we get here!


            for (int x = 0; x < args.size(); ++x) {
                JavaTypeInstance unbound = args.get(x);
                JavaTypeInstance bound = boundArgs.get(x);
                if (unbound instanceof JavaGenericBaseInstance) {
                    JavaGenericBaseInstance unboundGeneric = (JavaGenericBaseInstance) unbound;
                    unboundGeneric.tryFindBinding(bound, this);
                }
            }
        }
        return this;
    }

    public JavaTypeInstance getBindingFor(JavaTypeInstance maybeUnbound) {
        if (maybeUnbound instanceof JavaGenericPlaceholderTypeInstance) {
            JavaGenericPlaceholderTypeInstance placeholder = (JavaGenericPlaceholderTypeInstance) maybeUnbound;
            String name = placeholder.getRawName();
            JavaTypeInstance bound = nameToBoundType.get(name);
            if (bound != null) {
                return bound;
            }
        }
        return maybeUnbound;
    }

    public void addBindingFor(String name, JavaTypeInstance binding) {
        nameToBoundType.put(name, binding);
    }
}
