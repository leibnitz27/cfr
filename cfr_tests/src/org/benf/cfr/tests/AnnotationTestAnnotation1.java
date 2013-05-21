package org.benf.cfr.tests;

import java.lang.annotation.Annotation;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 22/03/2013
 * Time: 07:24
 * <p/>
 * This is not an annotation.  The only difference in the synthetic 'annotation' access flag.
 * Make sure it doesn't think it is! ;)
 */
public interface AnnotationTestAnnotation1 extends Annotation {
    String[] value();

    int fred();
}
