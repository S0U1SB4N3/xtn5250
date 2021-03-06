/*
Copyright 2007 Infordata S.p.A.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

/*
!!V 10/04/97 rel. 0.93 - some fix to add SignedNumeric fields handling.
    06/08/97 rel. 1.03c- bug fix.
    02/09/97 rel. 1.04a- changed to make XIReadFieldsCmd and XIReadImmediateCmd work.
    ***
    30/06/98 rel. _.___- Swing, JBuilder2 e VSS.
 */
 
 
package net.infordata.em.tn5250;

import java.io.IOException;
import java.io.OutputStream;

import net.infordata.em.crt5250.XI5250Field;
import net.infordata.em.crt5250.XI5250FieldSaver;
import net.infordata.em.crt5250.XIEbcdicTranslator;

/**
 * Implements XI5250FieldSaver to write fields content to an OutputStream.
 *
 * @see     XI5250Emulator#send5250Data
 *
 * @author   Valentino Proietti - Infordata S.p.A.
 */
public class XIFieldTo5250Stream implements XI5250FieldSaver {

  XI5250Emulator ivEmulator;
  OutputStream   ivOut;
  boolean        ivOnlyMDT;

  public XIFieldTo5250Stream(XI5250Emulator aEmulator, OutputStream aOutStream,
                             boolean onlyMDT) {
    ivEmulator = aEmulator;
    ivOut = aOutStream;
    ivOnlyMDT = onlyMDT;
  }

  public void write(XI5250Field aField, String aStr)
      throws IOException {
    if (ivOnlyMDT && !aField.isMDTOn())
      return;

    XIEbcdicTranslator translator = ivEmulator.getTranslator();

    // requires some special handling
    // see IBM 5250 function reference manual page 2-70
    if (aStr.length() > 0) {
      if (aField.isSignedNumeric()) {
        StringBuilder strBuf = new StringBuilder(aStr);
        int i;

        // find last digit char
        for (i = strBuf.length() - 1;
             (i >= 0) && !Character.isDigit(strBuf.charAt(i)); i--)
          ;

        // replace non digit chars between digit char with zeroes
        for (int j = i - 1; j >= 0; j--)
          if (!Character.isDigit(strBuf.charAt(j)))
            strBuf.setCharAt(j, '0');

        if (strBuf.charAt(strBuf.length() - 1) == '-') {
          if (i >= 0) {
            byte xx = translator.toEBCDIC(strBuf.charAt(i));
            xx &= 0x0F;
            xx |= 0xD0;
            strBuf.setCharAt(i, translator.toChar(xx));
            aStr = new String(strBuf).substring(0, strBuf.length() - 1);
          }
          else
            aStr = "";
        }
        else
          aStr = new String(strBuf);
      }
    }

    int i = aStr.length() - 1;

    if (ivOnlyMDT) {
      byte[] cBuf = {XI5250Emulator.ORD_SBA,
                     (byte)(aField.getRow() + 1),
                     (byte)(aField.getCol() + 1)};

      ivOut.write(cBuf);

      // exclude trailing null chars
      for (; (i >= 0) && (aStr.charAt(i) == '\u0000'); i--)
        ;
    }

    byte[] strBuf = new byte[i + 1];
    {
      int linearPos = ivEmulator.toLinearPos(aField.getCol(), aField.getRow());
      int    j;
      int    len = Math.min(i + 1, aStr.length());
      byte   space = translator.toEBCDIC(' ');

      for (j = 0; j < len; j++) {
        char ch = aStr.charAt(j);
        if (ch == XI5250Emulator.ATTRIBUTE_PLACE_HOLDER) {
          ch = (char)ivEmulator.getAttr(ivEmulator.toColPos(linearPos + j), 
              ivEmulator.toRowPos(linearPos + j));
          strBuf[j] = (byte)ch;   // leave attributes as is
        }
        else if (ch == '\u0000') {
          strBuf[j] = space;
        }
        else {
          strBuf[j] = translator.toEBCDIC(ch);
        }
      }
      // fill with space
      for (j = len; j < i + 1; j++)
        strBuf[j] = space;
    }

    if (i >= 0) {
      for (int j = 0; j < (i + 1); j++) {
        // convert nulls to EBCDIC spaces
        if (strBuf[j] == 0)
          strBuf[j] = 0x40;
      }

      ivOut.write(strBuf, 0, i + 1);
    }
  }

}
