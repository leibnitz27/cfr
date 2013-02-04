package org.benf.cfr.tests;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 05/05/2011
 * Time: 18:48
 * To change this template use File | Settings | File Templates.
 */
/*
 Inner class produces:

 class org.benf.cfr.tests.SwitchTest2$1 {
  static final int[] $SwitchMap$org$benf$cfr$tests$EnumTest1;

  static {};
    Code:
       0: invokestatic  #1                  // Method org/benf/cfr/tests/EnumTest1.values:()[Lorg/benf/cfr/tests/EnumTest1;
       3: arraylength
       4: newarray       int
       6: putstatic     #2                  // Field $SwitchMap$org$benf$cfr$tests$EnumTest1:[I
       9: getstatic     #2                  // Field $SwitchMap$org$benf$cfr$tests$EnumTest1:[I
      12: getstatic     #3                  // Field org/benf/cfr/tests/EnumTest1.FOO:Lorg/benf/cfr/tests/EnumTest1;
      15: invokevirtual #4                  // Method org/benf/cfr/tests/EnumTest1.ordinal:()I
      18: iconst_1
      19: iastore
      20: goto          24
      23: astore_0
      24: getstatic     #2                  // Field $SwitchMap$org$benf$cfr$tests$EnumTest1:[I
      27: getstatic     #6                  // Field org/benf/cfr/tests/EnumTest1.BAP:Lorg/benf/cfr/tests/EnumTest1;
      30: invokevirtual #4                  // Method org/benf/cfr/tests/EnumTest1.ordinal:()I
      33: iconst_2
      34: iastore
      35: goto          39
      38: astore_0
      39: return
    Exception table:
       from    to  target type
           9    20    23   Class java/lang/NoSuchFieldError
          24    35    38   Class java/lang/NoSuchFieldError
}

 */
public class SwitchTest1 {


    // LookupSwitch
    public void test1(int x) {
        switch (x) {
            case 1:
                System.out.println("One");   // Fall through
            case 3:
                System.out.println("Three");
                break;
            case 7:
                System.out.println("Seven");
                break;
            case 11214:
            case 5000:
                System.out.println("FiveK"); // Fall through
            default:
                System.out.println("Default");

        }
    }


    // Tableswitch
    public void test2(int x) {
        switch (x) {
            case 1:
                System.out.println("One");   // Fall through
            case 3:
                System.out.println("Three");
                break;
            case 6:
            case 7:
                System.out.println("Seven");
                break;
            case 5:
                System.out.println("Five"); // Fall through
            default:
                System.out.println("Default");

        }
    }

}
