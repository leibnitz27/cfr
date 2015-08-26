package org.benf.cfr.reader;

import org.benf.cfr.reader.api.ClassFileSource;
import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.state.ClassFileSourceImpl;
import org.benf.cfr.reader.state.DCCommonState;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.state.TypeUsageInformation;
import org.benf.cfr.reader.util.MapFactory;
import org.benf.cfr.reader.util.getopt.Options;
import org.benf.cfr.reader.util.getopt.OptionsImpl;
import org.benf.cfr.reader.util.output.*;

import java.util.Map;

public class PluginRunner {
    private final DCCommonState dcCommonState;
    private final IllegalIdentifierDump illegalIdentifierDump = new IllegalIdentifierDump.Nop();

    /*
     *
     */
    public PluginRunner() {
        this(MapFactory.<String, String>newMap(), null);
    }

    public PluginRunner(Map<String, String> options) {
        this(options, null);
    }

    public PluginRunner(Map<String, String> options, ClassFileSource classFileSource) {
        this.dcCommonState = initDCState(options, classFileSource);
    }

    public void addJarPaths(String[] jarPaths) {
        for (String jarPath : jarPaths) {
            addJarPath(jarPath);
        }
    }

    public void addJarPath(String jarPath) {
        try {
            dcCommonState.explicitlyLoadJar(jarPath);
        } catch (Exception e) {
        }
    }

    public String getDecompilationFor(String classFilePath) {
        try {
            ClassFile c = dcCommonState.getClassFile(classFilePath);
            c = dcCommonState.getClassFile(c.getClassType());
            c.loadInnerClasses(dcCommonState);

            // THEN analyse.
            c.analyseTop(dcCommonState);
            /*
             * Perform a pass to determine what imports / classes etc we used / failed.
             */
            TypeUsageCollector collectingDumper = new TypeUsageCollector(c);
            c.collectTypeUsages(collectingDumper);

            final StringBuffer outBuffer = new StringBuffer();
            class StringStreamDumper extends StreamDumper {
                public StringStreamDumper(TypeUsageInformation typeUsageInformation, Options options) {
                    super(typeUsageInformation, options, illegalIdentifierDump);
                }

                @Override
                protected void write(String s) {
                    outBuffer.append(s);
                }

                @Override
                public void close() {
                }

                @Override
                public void addSummaryError(Method method, String s) {
                }
            }

            Dumper d = new StringStreamDumper(collectingDumper.getTypeUsageInformation(), dcCommonState.getOptions());
            c.dump(d);
            return outBuffer.toString();
        } catch (Exception e) {
            return e.toString();
        }
    }

    private static DCCommonState initDCState(Map<String, String> optionsMap, ClassFileSource classFileSource) {
        OptionsImpl options = new OptionsImpl(null, null, optionsMap);
        if (classFileSource == null) classFileSource = new ClassFileSourceImpl(options);
        DCCommonState dcCommonState = new DCCommonState(options, classFileSource);
        return dcCommonState;
    }

}
