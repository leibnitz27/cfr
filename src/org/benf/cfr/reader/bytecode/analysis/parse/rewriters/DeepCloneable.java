package org.benf.cfr.reader.bytecode.analysis.parse.rewriters;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 04/07/2013
 * Time: 17:25
 */
public interface DeepCloneable<X> {
    X deepClone(CloneHelper cloneHelper);

    X outerDeepClone(CloneHelper cloneHelper);
}
