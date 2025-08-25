package com.than;

/**
 * socket消息的包装类
 */
public class SocketMassage {

    private String dataName;
    private String data;

    public static final String HELLO_TEXT = "Hello";
    public static final String CONFIG_TEXT = "Config";
    public static final String CONFIG_SAVE_ERROR_TEXT = "Config Error";
    public static final String UPLOAD_START_TEXT = "Upload Start";
    public static final String UPLOAD_END_TEXT = "Upload End";
    public static final String FILE_DOWNLOAD_TEXT = "Download";


    public static final SocketMassage HELLO=new SocketMassage(HELLO_TEXT);
    public static final SocketMassage CONFIG=new SocketMassage(CONFIG_TEXT);
    public static final SocketMassage CONFIG_SAVE_ERROR=new SocketMassage(CONFIG_SAVE_ERROR_TEXT);
    public static final SocketMassage UPLOAD_START=new SocketMassage(UPLOAD_START_TEXT);
    public static final SocketMassage UPLOAD_END=new SocketMassage(UPLOAD_END_TEXT);
    public static final SocketMassage FILE_DOWNLOAD=new SocketMassage(FILE_DOWNLOAD_TEXT);


    public SocketMassage(String dataName) {
        this.dataName = dataName;
    }

    public String getDataName() {
        return dataName;
    }

    public void setDataName(String dataName) {
        this.dataName = dataName;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    @Override
    public String toString() {
        return "SocketMassage{" +
                "dataName='" + dataName + '\'' +
                ", data='" + data + '\'' +
                '}';
    }
}
