package org.benf.cfr.tests;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 05/05/2011
 * Time: 06:28
 * To change this template use File | Settings | File Templates.
 */
public class ExceptionTest1 {

    void test1(String path) {
        try {
            File file = new File(path);
            FileInputStream fileInputStream = new FileInputStream(file);
        } catch (FileNotFoundException e) {
            System.out.println("File Not found");
        }
    }


}
