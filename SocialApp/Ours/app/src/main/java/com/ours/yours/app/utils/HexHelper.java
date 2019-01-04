package com.ours.yours.app.utils;

public class HexHelper {
    private static final HexHelper INSTANCE = new HexHelper();
    private static final char[] mHexChars = "0123456789abcdef".toCharArray();

    public static String byteToHexString(byte value){
        StringBuilder sb = new StringBuilder();
        sb.append("0x");
        sb.append(mHexChars[(value & 0xff) >> 4]);
        sb.append(mHexChars[value & 0x0f]);
        return sb.toString().trim().toLowerCase();
    }

    public static HexHelper getInstance() {
        return INSTANCE;
    }

    public static byte hexStringToByte(String src){
        byte ret;
        src =  "0x" + src.trim().replace(" ", "").toLowerCase();
        ret = (byte) (Integer.decode(src) & 0xff);
        //Logger.d("... hexStringToByte(), this input src to byte is " + byteToHexString(ret));
        return ret;
    }

    public static boolean checkAddressIsHexStr(String src){
        boolean ret = false;
        boolean flagIsHex = false;
        flagIsHex = checkIsHex(src);
        int srcInt = 990;

        if (flagIsHex) {
            byte srcByte = hexStringToByte(src);
            srcInt = srcByte & 0xff;
            ret = true;
        } else {
            ret = false;

        }

        //Logger.d("... checkAddressIsHexStr(), srcInt is " + String.valueOf(srcInt) + " and ret = " + ret);
        return ret;

    }

    /**
     * Java adds sign-extended with bit-shift while converting bytes to int prior to being ,
     * for example, 0x88 -> FFFFFF88, so it need to mask out to extract
     */
    public static boolean checkValueIsHexStr(String src){
        boolean ret = false;
        boolean flagIsHex = false;
        flagIsHex = checkIsHex(src);
        int srcInt = 991;
        if (flagIsHex) {
            byte srcByte = hexStringToByte(src);
            srcInt = srcByte & 0xff;
            ret = true;
        }
        return ret;

    }

    public static boolean checkIsHex(String src){
        boolean ret = false;
        if(src.length() == 2){
            char src0 = src.charAt(0);
            int intSrc0 = Character.getNumericValue(src0);
            //Logger.d("... checkIsHex(), src0 is " + src0 + " and ret = " + ret);
            //Logger.d("... checkIsHex(), intSrc0 is " + intSrc0 + " and ret = " + ret);
            char src1 = src.charAt(1);
            int intSrc1 = Character.getNumericValue(src1);
            //Logger.d("... checkIsHex(), src1 is " + src1 + " and ret = " + ret);
            //Logger.d("... checkIsHex(), intSrc1 is " + intSrc1 + " and ret = " + ret);
            ret = (intSrc0 < 16 && intSrc0 >= 0) ? true : false;
            ret &= (intSrc1 < 16 && intSrc1 >= 0) ? true : false;
            //Logger.d("... checkIsHex(), after, ret = " + ret);
        }
        else if(src.length() == 1){
            char src0 = src.charAt(0);
            int intSrc0 = Character.getNumericValue(src0);
            //Logger.d("... checkIsHex(), src0 is " + src0 + " and ret = " + ret);
            //Logger.d("... checkIsHex(), intSrc0 is " + intSrc0 + " and ret = " + ret);
            ret = (intSrc0 < 16 && intSrc0 >= 0) ? true : false;
            //Logger.d("... checkIsHex(), after, ret = " + ret);

        }
        else{
            ret = false;
        }
        return ret;
    }
}
