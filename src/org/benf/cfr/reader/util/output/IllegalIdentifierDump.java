package org.benf.cfr.reader.util.output;

import org.benf.cfr.reader.util.getopt.Options;
import org.benf.cfr.reader.util.getopt.OptionsImpl;

public interface IllegalIdentifierDump {
    String getLegalIdentifierFor(String identifier);

    class Nop implements IllegalIdentifierDump {
        @Override
        public String getLegalIdentifierFor(String identifier) {
            return identifier;
        }
    }

    class Factory {
        public static IllegalIdentifierDump get(Options options) {
            if (options.getOption(OptionsImpl.RENAME_ILLEGAL_IDENTS)) {
                return IllegalIdentifierReplacement.getInstance();
            } else {
                return new Nop();
            }
        }
    }
}
