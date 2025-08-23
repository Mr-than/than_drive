package com.than;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Config {
    private List<String> rootFolders;
    private long lastUpdateTime;
    private long scanIntervalTime;
    private boolean bootUp;
    private int singleFileCount;

    private String downLoadPath;

    private String verify;

    public String getVerify() {
        return verify;
    }

    public void setVerify(String verify) {
        this.verify = verify;
    }

    /**
     * 需要附带端口
     */
    private String serverIp;

    public String getServerIp() {
        return serverIp;
    }

    public void setServerIp(String serverIp) {
        this.serverIp = serverIp;
    }


    public static Config getDefaultConfig() {
        Config config = new Config();
        config.addFolder("./");
        config.bootUp = true;
        config.lastUpdateTime = System.currentTimeMillis();
        config.scanIntervalTime = 600000;
        config.singleFileCount=5;
        config.downLoadPath = "./";
        config.serverIp = "http://127.0.0.1:8080";
        config.verify="Bd87FFE8B5FF67431D125AE62B8CD8AF";
        return config;
    }

    public Config() {
        rootFolders=new ArrayList<>();
    }

    public String getDownLoadPath() {
        return downLoadPath;
    }

    public void setDownLoadPath(String downLoadPath) {
        this.downLoadPath = downLoadPath;
    }

    public List<String> getRootFolders() {
        return rootFolders;
    }
    public void addFolder(String ... rootFolder) {
        rootFolders.addAll(Arrays.stream(rootFolder).toList());
    }
    public void removeFolder(String rootFolder) {
        rootFolders.remove(rootFolder);
    }

    private void setRootFolders(List<String> folders) {
        rootFolders.addAll(folders);
    }

    public long getLastUpdateTime() {
        return lastUpdateTime;
    }

    public void setLastUpdateTime(long lastUpdateTime) {
        this.lastUpdateTime = lastUpdateTime;
    }

    public long getScanIntervalTime() {
        return scanIntervalTime;
    }

    public void setScanIntervalTime(long scanIntervalTime) {
        this.scanIntervalTime = scanIntervalTime;
    }

    public boolean isBootUp() {
        return bootUp;
    }

    public void setBootUp(boolean bootUp) {
        this.bootUp = bootUp;
    }

    public int getSingleFileCount() {
        return singleFileCount;
    }

    public void setSingleFileCount(int singleFileCount) {
        this.singleFileCount = singleFileCount;
    }

}
