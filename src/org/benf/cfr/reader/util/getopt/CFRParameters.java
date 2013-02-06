package org.benf.cfr.reader.util.getopt;

import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 01/02/2013
 * Time: 16:29
 */
public class CFRParameters {

    private final String fileName;
    private final String methodName;
    private final Map<String, String> opts;

    public CFRParameters(String fileName, String methodName, Map<String, String> opts) {
        this.fileName = fileName;
        this.methodName = methodName;
        this.opts = opts;
    }

    public String getFileName() {
        return fileName;
    }

    public String getMethodName() {
        return methodName;
    }

    public boolean isNoStringSwitch() {
        return opts.containsKey("nostringswitch");
    }

    public boolean isLenient() {
        return false;
    }

    public boolean analyseMethod(String thisMethodName) {
        if (methodName == null) return true;
        return methodName.equals(thisMethodName);
    }
}
