package org.benf.cfr.reader;

import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.state.DCCommonState;
import org.benf.cfr.reader.util.CannotLoadClassException;
import org.benf.cfr.reader.util.ConfusedCFRException;
import org.benf.cfr.reader.util.getopt.BadParametersException;
import org.benf.cfr.reader.util.getopt.Options;
import org.benf.cfr.reader.util.getopt.GetOptParser;
import org.benf.cfr.reader.util.getopt.OptionsImpl;
import org.benf.cfr.reader.util.output.Dumper;
import org.benf.cfr.reader.util.output.DumperFactory;
import org.benf.cfr.reader.util.output.ToStringDumper;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 15/04/2011
 * Time: 18:15
 * To change this template use File | Settings | File Templates.
 */
public class Main {

    public static void doClass(DCCommonState dcCommonState, String path) {
        Options options = dcCommonState.getOptions();
        try {
            ClassFile c = dcCommonState.getClassFileMaybePath(path);
            dcCommonState.configureWith(c);

            // This may seem odd, but we want to make sure we're analysing the version
            // from the cache.
            try {
                c = dcCommonState.getClassFile(c.getClassType());
                if (options.getOption(OptionsImpl.DECOMPILE_INNER_CLASSES)) {
                    c.loadInnerClasses(dcCommonState);
                }
            } catch (CannotLoadClassException e) {
            }
            // THEN analyse.
            c.analyseTop(dcCommonState);
            /*
             * Perform a pass to determine what imports / classes etc we used / failed.
             */
            TypeUsageCollector collectingDumper = new TypeUsageCollector(c);
            c.collectTypeUsages(collectingDumper);

            Dumper d = DumperFactory.getNewTopLevelDumper(options, c.getClassType(), collectingDumper.getTypeUsageInformation());
            String methname = options.getMethodName();
            if (methname == null) {
                c.dump(d);
            } else {
                try {
                    for (Method method : c.getMethodByName(methname)) {
                        method.dump(d, true);
                    }
                } catch (NoSuchMethodException e) {
                    throw new BadParametersException("No such method '" + methname + "'.", OptionsImpl.getFactory());
                }
            }
            d.print("");
        } catch (CannotLoadClassException e) {
            System.err.println(e.toString());
        } catch (BadParametersException e) {
            System.err.print(e.toString());
        } catch (ConfusedCFRException e) {
            System.err.println(e.toString());
            for (Object x : e.getStackTrace()) {
                System.err.println(x);
            }
        } catch (Dumper.CannotCreate e) {
            System.err.print("Cannot create dumper " + e.toString());
        } catch (RuntimeException e) {
            System.err.print(e.toString());
        }
    }

    public static void doJar(DCCommonState dcCommonState, String path) {
        Options options = dcCommonState.getOptions();
        try {
            List<JavaTypeInstance> types = dcCommonState.explicitlyLoadJar(path);

            for (JavaTypeInstance type : types) {
                Dumper d = new ToStringDumper();
                try {
                    ClassFile c = dcCommonState.getClassFile(type);
                    if (options.getOption(OptionsImpl.DECOMPILE_INNER_CLASSES)) {
                        c.loadInnerClasses(dcCommonState);
                    }
                    // THEN analyse.
                    c.analyseTop(dcCommonState);

                    TypeUsageCollector collectingDumper = new TypeUsageCollector(c);
                    c.collectTypeUsages(collectingDumper);
                    d = DumperFactory.getNewTopLevelDumper(options, c.getClassType(), collectingDumper.getTypeUsageInformation());

                    c.dump(d);
                    d.print("\n\n");
                } catch (Dumper.CannotCreate e) {
                    throw e;
                } catch (RuntimeException e) {
                    d.print(e.toString()).print("\n\n\n");
                } finally {
                    d.close();
                }

            }
        } catch (RuntimeException e) {
            System.err.print("Exception analysing jar " + e);
        }
    }

    public static void main(String[] args) {

        GetOptParser getOptParser = new GetOptParser();

        Options options = null;
        try {
            options = getOptParser.parse(args, OptionsImpl.getFactory());
        } catch (Exception e) {
            System.err.print(e);
            System.exit(1);
        }

        DCCommonState dcCommonState = new DCCommonState(options);
        String path = options.getFileName();
        if (dcCommonState.isJar(path)) {
            doJar(dcCommonState, path);
        } else {
            doClass(dcCommonState, path);
        }

    }
}
