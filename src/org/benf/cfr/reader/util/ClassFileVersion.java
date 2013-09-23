package org.benf.cfr.reader.util;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 11/04/2013
 * Time: 18:19
 */
public class ClassFileVersion {
    private final int major;
    private final int minor;

    public ClassFileVersion(int major, int minor) {
        this.major = major;
        this.minor = minor;
    }

    public boolean equalOrLater(ClassFileVersion other) {
        if (this.major < other.major) return false;
        if (this.major > other.major) return true;
        if (this.minor < other.minor) return false;
        return true;
    }

    public boolean before(ClassFileVersion other) {
        return !equalOrLater(other);
    }

    @Override
    public String toString() {
        return "" + major + "." + minor;
    }

    public static ClassFileVersion JAVA_1_4 = new ClassFileVersion(48, 0); // 48->49
    public static ClassFileVersion JAVA_5 = new ClassFileVersion(49, 0); // 48->49
    public static ClassFileVersion JAVA_6 = new ClassFileVersion(50, 0); // 48->49
    public static ClassFileVersion JAVA_7 = new ClassFileVersion(51, 0); // 48->49
    public static ClassFileVersion JAVA_8 = new ClassFileVersion(51, 0); // 48->49
}
