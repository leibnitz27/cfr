package org.benf.cfr.reader.bytecode.analysis.types;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 05/03/2014
 * Time: 17:45
 */
public class InnerClassInfoUtils {
    public static JavaRefTypeInstance getTransitiveOuterClass(JavaRefTypeInstance type) {
        while (type.getInnerClassHereInfo().isInnerClass()) {
            type = type.getInnerClassHereInfo().getOuterClass();
        }
        return type;
    }
}
