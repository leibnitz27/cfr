package org.benf.cfr.reader.util.getopt;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 01/02/2013
 * Time: 17:02
 */
public class BadParametersException extends IllegalArgumentException {
    private final PermittedOptionProvider permittedOptionProvider;

    public BadParametersException(String s, PermittedOptionProvider permittedOptionProvider) {
        super(s);
        this.permittedOptionProvider = permittedOptionProvider;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getMessage()).append("\n\ncfr filename [ methname ]\n").append(GetOptParser.getHelp(permittedOptionProvider));
        return sb.toString();
    }
}
