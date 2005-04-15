/*
 * Copyright 2003 Sun Microsystems, Inc. All rights reserved.
 * SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

import java.io.IOException;

import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;
import com.sun.codemodel.JVar;
import com.sun.codemodel.*;
import com.sun.codemodel.writer.SingleStreamCodeWriter;

/**
 * 
 * Simple program to test the generation of
 * the varargs feature in jdk 1.5
 * @author Bhakti Mehta Bhakti.Mehta@sun.com
 *
 */
/*======================================================
 * This is how the output from this program looks like
 * Still need to learn how to work on instantiation and args
 * =========================================================
 * public class Test {


     public void foo(java.lang.String param1, 
         java.lang.Integer param2, java.lang.String param5,
         java.lang.Object... param3) {
      for (int count = 0; (count<(param3.length)); count ++) {
          java.lang.System.out.println((param3[count]));
      }
  }

    public static void main(java.lang.String[] args) {
    }

}
*==========================================================
**/

public class VarArgsTest {

    public static void main(String[] args)  {

        try {
            JCodeModel cm = new JCodeModel();
            JDefinedClass cls = cm._class("Test");
            JMethod m = cls.method(JMod.PUBLIC, cm.VOID, "foo");
            m.param(String.class, "param1");
            m.param(Integer.class, "param2");
            JVar var = m.varParam(Object.class, "param3");
            System.out.println("First varParam " + var);
            
            // checking for param after varParam it behaves ok
            //JVar[] var1 = m.varParam(Float.class, "param4");
            JClass string = cm.ref(String.class);
            JClass stringArray = string.array();
            JVar param5 = m.param(String.class, "param5");
            
            JForLoop forloop = m.body()._for();
            
            JVar $count = forloop.init(cm.INT, "count", JExpr.lit(0));
            
            forloop.test($count.lt(JExpr.direct("param3.length")));
            forloop.update($count.incr());
            
            JFieldRef out = cm.ref(System.class).staticRef("out");
            
            JVar typearray = m.listVarParam();
            
            JInvocation invocation =
                forloop.body().invoke(out, "println").arg(
                    JExpr.direct("param3[count]"));
            
            JMethod main = cls.method(JMod.PUBLIC | JMod.STATIC, cm.VOID, "main");
            main.param(stringArray, "args");
            main.body().directStatement("new Test().foo(new String(\"Param1\"),new Integer(5),null,new String(\"Param3\"),new String(\"Param4\"));" );//new String("Param1"))"");//                "new Integer(5),+//                "null," +//                "new String("first")," +//                " new String("Second"))");
            
            cm.build(new SingleStreamCodeWriter(System.out));
        } catch (JClassAlreadyExistsException e) {
            
            e.printStackTrace();
        } catch (IOException e) {
            
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
