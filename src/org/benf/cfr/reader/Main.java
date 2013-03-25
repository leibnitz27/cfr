package org.benf.cfr.reader;

import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.util.CannotLoadClassException;
import org.benf.cfr.reader.util.ConfusedCFRException;
import org.benf.cfr.reader.util.getopt.BadParametersException;
import org.benf.cfr.reader.util.getopt.CFRState;
import org.benf.cfr.reader.util.getopt.GetOptParser;
import org.benf.cfr.reader.util.output.Dumper;

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
            CFRState params = getOptParser.parse(args, CFRState.getFactory());
            ClassFile c = params.getClassFile(params.getFileName());

            c.analyseTop(params);
            Dumper d = new Dumper();
            String methname = params.getMethodName();
            if (methname == null) {
                c.dump(d);
            } else {
                try {
                    c.getMethodByName(methname).dump(d, c.getConstantPool());
                } catch (NoSuchMethodException e) {
                    throw new BadParametersException("No such method '" + methname + "'.", CFRState.getFactory());
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
