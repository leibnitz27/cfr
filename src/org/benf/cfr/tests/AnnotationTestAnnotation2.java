package org.benf.cfr.tests;

import java.util.HashMap;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 22/03/2013
 * Time: 07:24
 */
public @interface AnnotationTestAnnotation2 {
    String value() default "wibble";

    public final int x = 3; // Seems odd, but need to get it right!
    static final int y = 3; // Seems odd, but need to get it right!
    public Map<String, Integer> z = new HashMap<String, Integer>();

    public class fred {
        // INNER CLASS ON ANNOTATION?  *sigh*
        private static final int TEST = 3;
        private int y;

        public fred(int y) {
            this.y = y;
        }
    }
}
