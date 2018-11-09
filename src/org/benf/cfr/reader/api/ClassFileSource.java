package org.benf.cfr.reader.api;

import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.benf.cfr.reader.state.ClassFileSourceImpl;
import org.benf.cfr.reader.util.getopt.Options;
import org.benf.cfr.reader.util.getopt.OptionsImpl;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

/**
 * Implementing your own {@link ClassFileSource} allows you to override CFR's sourcing of byte code.
 * You could even generate bytecode on the fly and provide it to CFR.
 *
 * If you wish to delegate to a 'normal' {@link ClassFileSource}, you may get one from {@link Factory}
 *
 * Note that implementations of this object that provide more than one class are likely to have to be
 * mutable, as they need to keep track of the relationship between classes and directories.
 */
@SuppressWarnings({"WeakerAccess", "unused"})
public interface ClassFileSource {
    /**
     * CFR has loaded your class, and it has decided that if it were in the correct location, it would instead
     * be at {@param classFilePath}.
     *
     * This information should be taken into account when loading any inner classes, or other classes that CFR tries
     * to load in order to improve decompilation.
     *
     * Why do you care?  Let's say you have a class in a top level outside its normal structure, Bob.class.
     * It should be found at org/person/Bob.class.
     *
     * Implementors of this will be called back with "", and "org/person", telling you that org.person are implicit
     * in your papth.  CFR may later ask you to load "org/person/Bob$1.class".
     *
     * You should adjust this path to match where Bob$1 actually is.
     *
     * <em>This will also be called with null, null to reset.</em>
     *
     * @param usePath the path that was used to load a class file.
     * @param classFilePath the path that CFR actually suspects it should have been, based on package name.
     */
    void informAnalysisRelativePathDetail(String usePath, String classFilePath);

    /**
     * CFR would like to know about all classes contained within the jar at {@param jarPath}
     *
     * @param jarPath path to a jar.
     * @return paths (inside jar) of all classes.
     */
    Collection<String> addJar(String jarPath);

    /**
     * It's possible that an obfuscator might have generated a bizarre and magic file inside a jar such that
     * it's path is too big to read.  Or it's invalid.
     *
     * This allows you to remap paths.
     *
     * @param path Path CFR would like to use
     * @return Remapped path.
     */
    String getPossiblyRenamedPath(String path);

    /**
     * Given a path to a class file, return a pair of
     * * the content, as a byte array.
     * * the path where you found this.
     *
     * @param path relative path of class we wish to load.
     * @return Pair<byte[], String> of class file content, and file location.
     * @throws IOException if you can't find the class.
     */
    Pair<byte[], String> getClassFileContent(final String path) throws IOException;

    /**
     * Helper to construct "Standard" {@link ClassFileSource}, which may be useful for delegating to.
     */
    class Factory {
        /**
         * @param options Map of options, as per {@link CfrDriver}'s builder.
         * @return CFR's internal implementation of {@link ClassFileSource}
         */
        static ClassFileSource createInternalClassFileSource(Map<String, String> options) {
            Options parsedOptions = OptionsImpl.getFactory().create(options);
            return new ClassFileSourceImpl(parsedOptions);
        }
    }
}
