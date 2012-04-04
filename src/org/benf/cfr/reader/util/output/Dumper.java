package org.benf.cfr.reader.util.output;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 18/04/2011
 * Time: 11:24
 * To change this template use File | Settings | File Templates.
 */
public class Dumper {
    public void print(String s)
    {
        System.out.print(s);
    }
    public Dumper newln()
    {
        System.out.println("");
        return this;
    }
    public void line()
    {
        System.out.println("\n-------------------");
    }
}
