package com.than;


import java.io.IOException;

public class Main {

    private static final Logger logger = Logger.getLogger(Main.class);

    public static void main(String[] args) {

        try {
            boolean debug = true;
            if (!debug) {
                Base.autoBoot();
            }
            Logger.setLevel(Logger.Level.INFO);
            Logger.setLogFilePath("log.txt");
            logger.info("start......");

            FileManager fileManager = new FileManager();
            NetworkUtil networkUtil = new NetworkUtil();
            String mapText;

            logger.info("get file map");
            try {
                mapText = networkUtil.getFileMap();
            } catch (IOException e) {
                logger.error("get file map error", e);
                Util.showWindowsNotification("服务器可能遇到问题", "查看日志文件");
                logger.warn("start waiting server......");
                while (true) {
                    try {
                        mapText = networkUtil.getFileMap();
                        break;
                    } catch (Exception i) {
                        Thread.sleep(2000);
                    }
                }
            }

            fileManager.setFileMd5Map(mapText);
            SocketManager socketManager = new SocketManager(fileManager, networkUtil);
            logger.info("open socket");
            socketManager.start();
            logger.info("socket started");

            logger.info("start file scan");
            fileManager.start(networkUtil);
            logger.info("file scan started");
        } catch (Exception e) {
            logger.error("exception", e);
            Util.showWindowsNotification("遇到一些问题", "查看日志文件");
        }
    }


}