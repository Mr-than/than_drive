package com.than;

import com.google.gson.Gson;

import java.io.IOException;
import java.net.SocketException;
import java.security.NoSuchAlgorithmException;

public class SocketManager extends Thread {

    private static final Logger logger = Logger.getLogger(SocketManager.class);

    private Gson gson = new Gson();
    private NetworkUtil networkUtil;

    @Override
    public void run() {
        try {
            startSocket();
        } catch (IOException|InterruptedException e) {
            logger.error("exception",e);
            Util.showWindowsNotification("发送错误",e.getMessage());
        }
    }

    private FileManager manager;

    public SocketManager(FileManager manager,NetworkUtil networkUtil) {
        this.manager = manager;
        this.networkUtil = networkUtil;
    }

    public void startSocket() throws IOException, InterruptedException {
        logger.info("listening socket");
        while (networkUtil.startServer()) {
            while (true) {
                try {
                    SocketMassage massage = acceptMessage();
                    handleMessage(massage);
                } catch (SocketException e) {
                    networkUtil.closeSocket();
                    break;
                } catch (NoSuchAlgorithmException e) {
                    logger.error("exception",e);
                }
            }
        }
    }


    private SocketMassage acceptMessage() throws IOException {
        return networkUtil.acceptMessage();
    }

    private void handleMessage(SocketMassage massage) throws IOException, InterruptedException, NoSuchAlgorithmException {
        switch (massage.getDataName()) {
            case SocketMassage.HELLO_TEXT: {
                logger.info("socket get hello");
                SocketMassage.CONFIG.setData(gson.toJson(FileManager.getConfig()));
                networkUtil.sendMassage(SocketMassage.CONFIG);
                break;
            }
            case SocketMassage.CONFIG_TEXT: {
                logger.info("socket get config");
                FileManager.writeToConfig(massage.getData());
                break;
            }
            case SocketMassage.UPLOAD_START_TEXT: {
                logger.info("socket get upload");
                networkUtil.sendMassage(SocketMassage.UPLOAD_START);
                FileManager.startScan(manager,networkUtil);
                networkUtil.sendMassage(SocketMassage.UPLOAD_END);
                SocketMassage.CONFIG.setData(gson.toJson(FileManager.getConfig()));
                networkUtil.sendMassage(SocketMassage.CONFIG);
                break;
            }

            case SocketMassage.FILE_DOWNLOAD_TEXT: {
                logger.info("socket get file download");
                String data = massage.getData();
                String[] parts = data.trim().split("\u0000", 2);
                networkUtil.downloadFile(parts[0],parts[1]);
                Util.showWindowsNotification(parts[1]+"下载完成","下载至 "+ FileManager.getConfig().getDownLoadPath());
            }
        }
    }
}
