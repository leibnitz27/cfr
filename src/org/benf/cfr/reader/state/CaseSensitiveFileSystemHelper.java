package org.benf.cfr.reader.state;

public class CaseSensitiveFileSystemHelper {
    /*
     * Are you good?  Be good.  Good is good.
     * Unfortunately, only linux ships with a sensible file system.
     * (Yes, HPFS, I'm looking at you).
     */
    public static boolean IsCaseSensitive() {
        /*
         * There's actually no sensible API to determine this - documentation leaves
         * it as implementation defined.  So use a crappy heuristic.
         */
        String osname = System.getProperty("os.name", "").toLowerCase();
        if (osname.contains("windows")) return true;
        // I know, it CAN be done.  But out of the box, macosen assume case
        // insensitivity.
        if (osname.contains("mac")) return true;
        return false;
    }
}
