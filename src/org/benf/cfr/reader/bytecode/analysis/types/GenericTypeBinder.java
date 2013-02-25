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

    public GenericTypeBinder(ClassSignature classSignature, JavaGenericRefTypeInstance boundInstance) {
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
}
