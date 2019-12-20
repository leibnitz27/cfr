package org.benf.cfr.reader.state;

import org.benf.cfr.reader.util.collections.SetFactory;

import java.util.Collections;
import java.util.Set;

public class OsInfo {

    /*
     * Are you good?  Be good.  Good is good.
     * Unfortunately, only linux ships with a sensible file system.
     * (Yes, HPFS, I'm looking at you).
     */
    public enum OS {
        // See https://docs.microsoft.com/en-us/windows/win32/fileio/naming-a-file#naming-conventions
        // and https://docs.microsoft.com/en-us/windows/win32/api/fileapi/nf-fileapi-createfilea
        WINDOWS(true, SetFactory.newSet(
            "con",
            "aux",
            "prn",
            "nul",
            "com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9",
            "lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9",
            "conin$", "conout$"
        )),
        OSX(true, Collections.<String>emptySet()),
        OTHER(false, Collections.<String>emptySet()); // I'm assuming other behaves.  If it doesn't, add it.

        private boolean caseInsensitive;
        private Set<String> illegalNames;

        OS(boolean caseInsensitive, Set<String> illegalNames) {
            this.caseInsensitive = caseInsensitive;
            this.illegalNames = illegalNames;
        }

        public boolean isCaseInsensitive() {
            return caseInsensitive;
        }

        public Set<String> getIllegalNames() {
            return illegalNames;
        }
    }

    public static OS OS() {
        /*
         * There's actually no sensible API to determine this - documentation leaves
         * it as implementation defined.  So use a crappy heuristic.
         */
        String osname = System.getProperty("os.name", "").toLowerCase();
        if (osname.contains("windows")) return OS.WINDOWS;
        // I know, it CAN be done.  But out of the box, macosen assume case
        // insensitivity.
        if (osname.contains("mac")) return OS.OSX;
        return OS.OTHER;
    }
}
