package org.benf.cfr.reader;

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
import org.benf.cfr.reader.util.output.StdOutDumper;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 15/04/2011
 * Time: 18:15
 * To change this template use File | Settings | File Templates.
 */
public class Main {

    public static void main(String[] args) {

        GetOptParser getOptParser = new GetOptParser();

        // Load the file, and pass the raw byteStream to the ClassFile constructor
        try {
            Options options = getOptParser.parse(args, OptionsImpl.getFactory());

            DCCommonState dcCommonState = new DCCommonState(options);
            String path = options.getFileName();
            ClassFile c = dcCommonState.getClassFileMaybePath(path);
            dcCommonState.configureWith(c);

            // This may seem odd, but we want to make sure we're analysing the version
            // from the cache.
            try {
                c = dcCommonState.getClassFile(c.getClassType());
                if (options.analyseInnerClasses()) {
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

            Dumper d = new StdOutDumper(collectingDumper.getTypeUsageInformation());
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
            System.exit(1);
        } catch (BadParametersException e) {
            System.err.print(e.toString());
        } catch (ConfusedCFRException e) {
            System.err.println(e.toString());
            for (Object x : e.getStackTrace()) {
                System.err.println(x);
            }
            System.exit(1);
        }


    }
}
