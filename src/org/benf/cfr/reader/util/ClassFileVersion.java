package org.benf.cfr.reader.util;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 11/04/2013
 * Time: 18:19
 */
public class ClassFileVersion {
    private final int major;
    private final int minor; // not used in comparison.

    public ClassFileVersion(int major, int minor) {
        this.major = major;
        this.minor = minor;
    }

    public boolean equalOrLater(ClassFileVersion other) {
        return this.major >= other.major;
    }

    public static ClassFileVersion JAVA_1_4 = new ClassFileVersion(48, 0); // 48->49
    public static ClassFileVersion JAVA_5 = new ClassFileVersion(49, 0); // 48->49
    public static ClassFileVersion JAVA_6 = new ClassFileVersion(50, 0); // 48->49
    public static ClassFileVersion JAVA_7 = new ClassFileVersion(51, 0); // 48->49
    public static ClassFileVersion JAVA_8 = new ClassFileVersion(51, 0); // 48->49
}
