package com.than;

import com.google.gson.Gson;

import java.io.IOException;
import java.net.SocketException;
import java.security.NoSuchAlgorithmException;

/**
 * socket管理类，控制接收消息后的动作，继承自Thread，为了不阻塞主线程
 */
public class SocketManager extends Thread {

    private static final Logger logger = Logger.getLogger(SocketManager.class);
    private Gson gson = new Gson();
    private NetworkUtil networkUtil;
    private FileManager manager;

    @Override
    public void run() {
        try {
            startSocket();
        } catch (IOException|InterruptedException e) {
            logger.error("exception",e);
            Util.showWindowsNotification("发送错误",e.getMessage());
        }
    }

    public SocketManager(FileManager manager,NetworkUtil networkUtil) {
        this.manager = manager;
        this.networkUtil = networkUtil;
    }

    /**
     * 开始socket监听
     */
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

    /**
     * 接受socket消息，没有消息时会阻塞
     * @return 接收到的socket消息
     * @throws IOException io异常
     */
    private SocketMassage acceptMessage() throws IOException {
        return networkUtil.acceptMessage();
    }

    /**
     * 处理接收到的socket消息，将其按照分类处理
     * @param massage socket消息
     * @throws IOException io异常
     */
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
                manager.startScan(networkUtil);
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
