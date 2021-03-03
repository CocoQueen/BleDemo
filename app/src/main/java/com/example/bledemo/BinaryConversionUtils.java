package com.example.bledemo;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class BinaryConversionUtils {
    //十六进制串转化为byte数组
    public static final byte[] hex2byte(String hex) {
        if (hex.length() % 2 != 0) {
            hex = "0" + hex;
        }
        char[] arr = hex.toCharArray();
        byte[] b = new byte[hex.length() / 2];
        for (int i = 0, j = 0, l = hex.length(); i < l; i++, j++) {
            String swap = "" + arr[i++] + arr[i];
            int byteint = Integer.parseInt(swap, 16) & 0xFF;
            b[j] = new Integer(byteint).byteValue();
        }
        return b;
    }

    //将传过来的十六进制串高地位不转换之后，计算干扰强度，存放到集合中
    public static List<Float> lowToUp(String oldStr) {//如 00008CC2 000005C3
        Log.i(">>>", oldStr.toString());
        List<Float> list = new ArrayList<>();

        for (int i = 0; i < oldStr.length(); i = i + 8) {
            String str = oldStr.substring(i, i + 8);//00008CC2/000005C3
            Log.e("--->str", str.toString());//0000B6C2
            String strr = "";
            for (int j = 0; j < str.length(); j = j + 2) {
                strr = str.substring(j, j + 2) + strr;
            }
            Log.e("--->Strr", strr);//四字节十六进制的信号干扰强度    C2B60000
            try {
//                strr转成十进制
                float a = byte2int_Float(hex2byte(strr));
                Log.e("--->转化之后的", a + "");
                if ((a + "").equals("-Infinity")) {
                    a = 0;
                }
                list.add(a);
                Log.i(">>>强度转十进制", byte2int_Float(hex2byte(strr)) + "");
            } catch (Exception e) {
                Log.i(">>>强度转十进制", "未成功！！！");
            }
        }
        return list;
    }

    //处理4个字节带小数的浮点数
    public static float byte2int_Float(byte b[]) {
        int bits = b[3] & 0xff | (b[2] & 0xff) << 8 | (b[1] & 0xff) << 16
                | (b[0] & 0xff) << 24;

        int sign = ((bits & 0x80000000) == 0) ? 1 : -1;
        int exponent = ((bits & 0x7f800000) >> 23);
        int mantissa = (bits & 0x007fffff);

        mantissa |= 0x00800000;
// Calculate the result:
        float f = (float) (sign * mantissa * Math.pow(2, exponent - 150));

        return f;
    }

    /**
     * 将byte数组化为十六进制串
     */

    public static final String byte2hex(byte[] data) {
        StringBuilder stringBuilder = new StringBuilder(data.length);
        for (byte byteChar : data) {
            stringBuilder.append(String.format("%02X ", byteChar).trim());
        }
        return stringBuilder.toString();
    }

    /**
     * @param src 16进制字符串
     * @return 字节数组
     * @throws
     * @Title:hexString2String
     * @Description:16进制字符串转字符串
     */
    public static String hexString2String(String src) {
        String temp = "";
        for (int i = 0; i < src.length() / 2; i++) {
            temp = temp
                    + (char) Integer.valueOf(src.substring(i * 2, i * 2 + 2),
                    16).byteValue();
        }
        return temp;
    }

    /**
     * 将字符串转成ASCII值
     */
    public static String strToASCII(String data) {
        String requestStr = "";
        for (int i = 0; i < data.length(); i++) {
            char a = data.charAt(i);
            int aInt = (int) a;
            requestStr = requestStr + integerToHexString(aInt);
        }
        return requestStr;
    }

    /**
     * 将十进制整数转为十六进制数，并补位
     */
    public static String integerToHexString(int s) {
        String ss = Integer.toHexString(s);
        if (ss.length() % 2 != 0) {
            ss = "0" + ss;//0F格式
        }
        return ss.toUpperCase();
    }
}
