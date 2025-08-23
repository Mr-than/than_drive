package com.than;

import java.awt.*;

public class Util {
    //private static final Logger logger = Logger.getLogger(Main.class);

    public static void showWindowsNotification(String title, String message){
        if (SystemTray.isSupported()) {
            try {
                SystemTray tray = SystemTray.getSystemTray();
                Image image = Toolkit.getDefaultToolkit().createImage(new byte[0]);
                TrayIcon trayIcon = new TrayIcon(image, "通知");
                trayIcon.setImageAutoSize(true);
                tray.add(trayIcon);

                trayIcon.displayMessage(title, message, TrayIcon.MessageType.INFO);

                // 等待一小会儿再移除，避免通知没来得及显示就消失
                Thread.sleep(3000);
                tray.remove(trayIcon);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            System.out.println("SystemTray 不支持！");
        }
    }

}
