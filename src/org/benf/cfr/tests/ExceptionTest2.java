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
public class ExceptionTest2 {

    void test1(String[] path) {
        try {
            File file = new File(path[0]);
            FileInputStream fileInputStream = new FileInputStream(file);
        } catch (FileNotFoundException e) {
            System.out.println("File Not found");
            for (String s : path) {
                System.out.println(s);
            }
        }
    }


}
