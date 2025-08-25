package com.than;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 这个类是获取或转换一些文件的信息
 */
public class FileData {
    /**
     * 获取文件的md5值，为了比对文件是否发生了变化
     * @param file 文件对象
     * @return md5值，字符串形式返回
     */
    public static String getMd5(File file) throws IOException, NoSuchAlgorithmException {
        if (!file.exists()) {
            throw new FileNotFoundException("文件不存在: " + file.getAbsolutePath());
        }

        if (file.isDirectory()) {
            throw new IOException("指定路径是目录，不是文件: " + file.getAbsolutePath());
        }

        String fileName = file.getName();
        MessageDigest md = MessageDigest.getInstance("MD5");
        md.update(fileName.getBytes("UTF-8"));
        try (InputStream is = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) > 0) {
                md.update(buffer, 0, read);
            }
        }
        return bytesToHex(md.digest());
    }

    /**
     * 将字节数组转换为十六进制字符串
     * @param bytes 字节数组
     * @return 十六进制字符串
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(32);
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xFF & b);
            if (hex.length() == 1) {
                sb.append('0');
            }
            sb.append(hex);
        }
        return sb.toString();
    }

}
