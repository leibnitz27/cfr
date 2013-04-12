package org.benf.cfr.reader.entities.bootstrap;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 12/04/2013
 * Time: 18:58
 */
public class BootstrapMethodInfo {
    private final short methodRef;
    private final short[] bootstrapArguments;

    public BootstrapMethodInfo(short methodRef, short[] bootstrapArguments) {
        this.methodRef = methodRef;
        this.bootstrapArguments = bootstrapArguments;
    }
}
