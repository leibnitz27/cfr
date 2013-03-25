package org.benf.cfr.tests;

import java.lang.annotation.Annotation;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 22/03/2013
 * Time: 07:24
 */
public interface AnnotationTestAnnotation1 extends Annotation {
    String[] value();

    int fred();
}
