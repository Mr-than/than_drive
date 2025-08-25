package com.than;

import com.google.gson.Gson;
import okhttp3.*;

import java.io.*;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 这是网络io的工具类，包括socket通信和http请求函数
 */
public class NetworkUtil {
    private static final Logger logger = Logger.getLogger(NetworkUtil.class);

    //这个是baseUrl
    private final String FILE_URL;

    //okhttp主类
    private final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    //socket，为了和ui通讯
    private ServerSocket serverSocket;
    private Socket accept;

    private final Gson gson = new Gson();

    public NetworkUtil() throws IOException {
        String serverIp = FileManager.getConfig().getServerIp();
        //格式化url
        if (serverIp == null || serverIp.isEmpty()) {
            FILE_URL = "http://127.0.0.1:8080/";
        } else {
            if (!serverIp.endsWith("/")) {
                serverIp += "/";
            }
            FILE_URL = serverIp;
        }
    }

    /**
     * 上传文件
     * @param path 文件绝对路径
     * @param md5 文件md5
     * @throws IOException 文件io异常
     */
    public void upload(String path, String md5) throws IOException {
        logger.info("upload");
        File fileToUpload = new File(path);
        MediaType mediaType = MediaType.parse("application/octet-stream");
        RequestBody fileBody = RequestBody.create(fileToUpload, mediaType);
        MultipartBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM) // 设置请求体类型
                .addFormDataPart("path", path)
                .addFormDataPart("md5", md5)
                .addFormDataPart("file", fileToUpload.getName(), fileBody)
                .build();

        Request request = new Request.Builder()
                .url(FILE_URL + "upload")
                .post(requestBody)
                .header("verify", FileManager.getConfig().getVerify())
                .build();
        try (Response response = CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                logger.error("Unexpected code", new RuntimeException(response.message() + " code: " + response.code() + " body: " + response.body().string()));
                Util.showWindowsNotification("遇到一些问题", "查看日志文件");
            }
        }
    }


    /**
     * 下载文件
     * @param downloadDir 下载文件全路径名
     * @param fileName 文件名称（指服务器中的文件名称，服务器中的文件名添加了上传时的时间戳）
     * @throws IOException io异常
     */
    public void downloadFile(String downloadDir, String fileName) throws IOException {
        logger.info("download file");
        String downLoadPath = FileManager.getConfig().getDownLoadPath();
        if (!downLoadPath.endsWith("/") && !downLoadPath.endsWith("\\")) {
            downLoadPath = downLoadPath + "/";
        }

        File file = new File(downLoadPath + fileName);

        long downloadedSize = file.exists() ? file.length() : 0;

        RequestBody requestBody = new FormBody.Builder()
                .add("path", downloadDir)
                .add("file_name", fileName)
                .add("offset", downloadedSize + "") // 这里可以根据已下载大小更新，为了断点续传
                .build();

        Request request = new Request.Builder()
                .url(FILE_URL + "download")
                .post(requestBody)
                .header("verify", FileManager.getConfig().getVerify())
                .build();

        try (Response response = CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                if (response.code() == 300) {
                    System.out.println("文件不存在");
                }
                logger.error("Unexpected code", new RuntimeException(response.message() + " code: " + response.code() + " body: " + response.body().string()));
                Util.showWindowsNotification("遇到一些问题", "查看日志文件");
            }

            try (InputStream inputStream = response.body().byteStream();
                 RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw")) {
                randomAccessFile.seek(downloadedSize);
                byte[] buffer = new byte[4096];
                int bytesRead;

                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    randomAccessFile.write(buffer, 0, bytesRead);
                }
            }
        }
    }

    /**
     * 心跳发送函数
     * @return 是否需要等待，true需要，false不需要
     * @throws IOException io异常
     */
    public boolean heart() throws IOException {
        Request request = new Request.Builder()
                .url(FILE_URL + "heart")
                .header("verify", FileManager.getConfig().getVerify())
                .build();
        try (Response response = CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                logger.error("Unexpected code", new RuntimeException(response.message() + " code: " + response.code() + " body: " + response.body().string()));
                Util.showWindowsNotification("遇到一些问题", "查看日志文件");
                return true;
            }
            return false;
        } catch (ConnectException e) {
            logger.error("server error: ", e);
            return true;
        }
    }


    /**
     * 获取md5 map的函数
     * @return md5 map的json
     * @throws IOException io异常
     */
    public String getFileMap() throws IOException {
        Request request = new Request
                .Builder()
                .url(FILE_URL + "get_map")
                .header("verify", FileManager.getConfig().getVerify())
                .get()
                .build();
        return getString(request);
    }


    /**
     * 同步删除文件
     * @param map 本次扫描到的文件
     * @return 更新后的md5 map的json
     * @throws IOException io异常
     */
    public String syncFileMap(Map<String, String> map) throws IOException {
        String json = gson.toJson(map);
        MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
        RequestBody requestBody = RequestBody.create(json, mediaType);


        Request request = new Request.Builder()
                .url(FILE_URL + "sync_file")
                .post(requestBody)
                .header("verify", FileManager.getConfig().getVerify())
                .build();
        return getString(request);
    }

    private String getString(Request request) throws IOException {
        String body;
        try (Response response = CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                logger.error("Unexpected code", new RuntimeException(response.message() + " code: " + response.code() + " body: " + response.body().string()));

                throw new IOException();
            }
            body = response.body().string();
        }
        return body;
    }

    /**
     * 开始监听ui的socket,没有连接时会阻塞
     * @return true成功连接到ui，false为发生异常时
     */
    public boolean startServer() {
        try {
            logger.info("start listing server on port 12345");
            serverSocket = new ServerSocket(12345);
            accept = serverSocket.accept();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 接受socket信息的函数,没有消息时会阻塞
     * @return socket消息实体
     * @throws IOException io异常
     */
    public SocketMassage acceptMessage() throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(accept.getInputStream()));
        String line = br.readLine();
        return gson.fromJson(line, SocketMassage.class);
    }

    /**
     * 发送socket消息
     * @param socketMassage 要发送的socket
     * @throws IOException io异常
     */
    public void sendMassage(SocketMassage socketMassage) throws IOException {
        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(accept.getOutputStream()));
        String json = gson.toJson(socketMassage);
        bw.write(json);
        bw.newLine();
        bw.flush();
    }

    /**
     * 关闭socket，方便下次连接
     * @throws IOException io异常
     */
    public void closeSocket() throws IOException {
        accept.close();
        serverSocket.close();
    }
}
