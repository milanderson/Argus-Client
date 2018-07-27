package sbu.irclient;

import android.util.Log;

public class BitMask {
    private int[][] lines;
    private int maskLen;

    public BitMask(String maskString){

        // interpret bitmask from string message
        maskLen = maskString.length() - maskString.replace("-", "").length();
        lines = new int[maskLen][];

        int lineIndex = 0;
        for(int line = 0; line < maskLen; line++) {
            String lineStr = maskString.substring(lineIndex, maskString.indexOf("-", lineIndex + 1));
            int lineItems = lineStr.length() - lineStr.replace(",", "").length();
            lines[line] = new int[lineItems];

            int maskItem = 0;
            int lastItem = 0;
            for (int item = 0; item < lineStr.length(); item++) {
                if (lineStr.charAt(item) == ',') {
                    if (item != lastItem) {
                        lines[line][maskItem] = Integer.parseInt(lineStr.substring(lastItem, item));
                        maskItem++;
                        lastItem = item + 1;
                    }
                }
            }

            lineIndex = maskString.indexOf("-", lineIndex + 1);
        }
    }

    public int[] getLine(int line){
        return lines[line];
    }

    public int getLength(){
        return maskLen;
    }
}
