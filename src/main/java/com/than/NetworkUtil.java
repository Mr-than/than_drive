package com.than;

import com.google.gson.Gson;
import okhttp3.*;

import java.io.*;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.TimeUnit;


public class NetworkUtil {
    private static final Logger logger = Logger.getLogger(NetworkUtil.class);

    private final String FILE_URL;

    private final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();


    private ServerSocket serverSocket;
    private Socket accept;

    private final Gson gson = new Gson();

    public NetworkUtil() throws IOException {
        String serverIp = FileManager.getConfig().getServerIp();
        if (serverIp == null || serverIp.isEmpty()) {
            FILE_URL = "http://127.0.0.1:8080/";
        } else {
            if (!serverIp.endsWith("/")) {
                serverIp += "/";
            }
            FILE_URL = serverIp;
        }
    }

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
                .add("offset", downloadedSize + "") // 这里可以根据已下载大小更新
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

    public boolean heart() throws IOException {
        Request request = new Request.Builder()
                .url(FILE_URL + "heart")
                .header("verify", FileManager.getConfig().getVerify())
                .build();
        try (Response response = CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                logger.error("Unexpected code", new RuntimeException(response.message() + " code: " + response.code() + " body: " + response.body().string()));
                Util.showWindowsNotification("遇到一些问题", "查看日志文件");
            }
            return true;
        } catch (ConnectException e) {
            logger.error("server error: ", e);
            return false;
        }
    }


    public String getFileMap() throws IOException {
        Request request = new Request
                .Builder()
                .url(FILE_URL + "get_map")
                .header("verify", FileManager.getConfig().getVerify())
                .get()
                .build();
        return getString(request);
    }


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

    public SocketMassage acceptMessage() throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(accept.getInputStream()));
        String line = br.readLine();
        return gson.fromJson(line, SocketMassage.class);
    }

    public boolean sendMassage(SocketMassage socketMassage) throws IOException {
        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(accept.getOutputStream()));
        String json = gson.toJson(socketMassage);
        bw.write(json);
        bw.newLine();
        bw.flush();
        return true;
    }

    /*public boolean sendErrorMessage(SocketMassage socketMassage) throws IOException {

    }*/

    public void closeSocket() throws IOException {
        accept.close();
        serverSocket.close();
    }
}
