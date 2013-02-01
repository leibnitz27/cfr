package org.benf.cfr.reader.util.getopt;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 01/02/2013
 * Time: 17:02
 */
public class BadParametersException extends IllegalArgumentException {
    private final GetOptParser parser;

    public BadParametersException(String s, GetOptParser p) {
        super(s);
        this.parser = p;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getMessage()).append("\n\ncfr filename [ methname ]").append(parser.getHelp());
        return sb.toString();
    }
}
