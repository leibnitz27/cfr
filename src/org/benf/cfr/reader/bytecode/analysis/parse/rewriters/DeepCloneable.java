package org.benf.cfr.reader.bytecode.analysis.parse.rewriters;

public interface DeepCloneable<X> {
    X deepClone(CloneHelper cloneHelper);

    // Outer deep clone exists to cause visitor action, as we've lost generic information at the
    // call site.
    X outerDeepClone(CloneHelper cloneHelper);
}
