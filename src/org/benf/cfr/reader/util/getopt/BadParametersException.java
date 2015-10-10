package org.benf.cfr.reader.util.getopt;

public class BadParametersException extends IllegalArgumentException {
    private final PermittedOptionProvider permittedOptionProvider;

    public BadParametersException(String s, PermittedOptionProvider permittedOptionProvider) {
        super(s);
        this.permittedOptionProvider = permittedOptionProvider;
    }

    @Override
    public String toString() {
        return getMessage();
    }
}
