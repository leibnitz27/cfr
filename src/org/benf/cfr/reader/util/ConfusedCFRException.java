package org.benf.cfr.reader.util;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 15/04/2011
 * Time: 18:34
 * To change this template use File | Settings | File Templates.
 */
public class ConfusedCFRException extends RuntimeException {
    public ConfusedCFRException(String s)
    {
        super(s);
    }
    public ConfusedCFRException(Exception e)
    {
        super(e);
    }
}
