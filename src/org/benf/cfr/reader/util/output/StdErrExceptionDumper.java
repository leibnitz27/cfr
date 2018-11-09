package org.benf.cfr.reader.util.output;

import org.benf.cfr.reader.util.CannotLoadClassException;

public class StdErrExceptionDumper implements ExceptionDumper {
    @Override
    public void noteException(String path, String comment, Exception e) {
        if (comment != null) {
            System.err.println(comment);
        }
        if (e instanceof CannotLoadClassException) {
            System.err.println("Can't load the class specified:");
            System.err.println(e.toString());
        } else {
            System.err.println(e.toString());
            for (Object x : e.getStackTrace()) {
                System.err.println(x);
            }
        }

    }
}
