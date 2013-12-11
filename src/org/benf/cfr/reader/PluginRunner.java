package org.benf.cfr.reader;

import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.state.DCCommonState;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.state.TypeUsageInformation;
import org.benf.cfr.reader.util.MapFactory;
import org.benf.cfr.reader.util.getopt.OptionsImpl;
import org.benf.cfr.reader.util.output.*;

import java.util.List;
import java.util.zip.ZipFile;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 11/12/2013
 * Time: 06:58
 */
public class PluginRunner {
    private DCCommonState dcCommonState = initDCState();

    /*
     *
     */
    public PluginRunner() {
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

    public String getDecompilationFor(String className) {
        try {
            ClassFile c = dcCommonState.getClassFile(className);
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
                public StringStreamDumper(TypeUsageInformation typeUsageInformation) {
                    super(typeUsageInformation);
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

            Dumper d = new StringStreamDumper(collectingDumper.getTypeUsageInformation());
            c.dump(d);
            return outBuffer.toString();
        } catch (Exception e) {
            return e.toString();
        }
    }

    private static DCCommonState initDCState() {
        OptionsImpl options = new OptionsImpl(null, null, MapFactory.<String, String>newMap());
        DCCommonState dcCommonState = new DCCommonState(options);
        return dcCommonState;
    }

}
