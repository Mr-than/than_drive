package com.than;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.function.Function;

public class FileManager {

    public static String configFileName = "config.json";
    private static Config config;

    //private final ArrayList<File> rootFolderList;

    private final ArrayDeque<File> fileList;

    private static Gson gson;

    private static final Logger logger = Logger.getLogger(FileManager.class);

    /**
     * 存储所有文件的md5值，方便比对,
     * 这个map可能会存储已经删除的文件夹里的文件
     */
    private Map<String, String> fileMd5Map;

    /**
     * 这个set存储的是每次扫描到的文件,扫描完成后会和服务器map进行一次同步，确保去掉map中已经删除掉的文件
     */
    private final HashSet<String> curFileSet;


    private static SystemResourceMonitor monitor = new SystemResourceMonitor();


    public FileManager() throws IOException {
        gson = new Gson();
        config = getConfig();
        List<String> rootFolders = config.getRootFolders();
        /*rootFolderList = new ArrayList<>();
        for (String rootFolder : rootFolders) {
            rootFolderList.add(new File(rootFolder));
        }*/
        fileList = new ArrayDeque<>();
        curFileSet =new HashSet<>();
    }

    public void setFileMd5Map(String map) {
        this.fileMd5Map = gson.fromJson(map, new TypeToken<>() {
        });
        fileMd5Map = (fileMd5Map == null) ? new HashMap<>() : fileMd5Map;
    }

    public static Config getConfig() throws IOException {
        if (config != null) {
            return config;
        }
        readConfig();
        return config;
    }

    public void updateLastUpdateTime(long lastUpdateTime) throws IOException {
        config = getConfig();
        config.setLastUpdateTime(lastUpdateTime);
        writeToConfig(config);
    }


    private static void writeToConfig(Config config) throws IOException {
        File file = new File("./" + configFileName);
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(file))) {
            bw.write(gson.toJson(config));
        }
    }

    public static void writeToConfig(String configText) throws IOException {
        Config config = gson.fromJson(configText, Config.class);
        if (config != null) {
            writeToConfig(config);
            FileManager.config=config;
        }
    }

    private static void readConfig() throws IOException {
        File file = new File("./" + configFileName);
        if (!file.exists()) {
            writeToConfig(Config.getDefaultConfig());
        }
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            StringBuilder configJson = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                configJson.append(line);
            }
            config = gson.fromJson(configJson.toString(), Config.class);
        }

    }


    /**
     * 扫描设置的根目录
     *
     * @return 是否目录需要更新，返回true表示需要，否则不需要
     */
    public ArrayDeque<File> scanFiles() throws IOException, NoSuchAlgorithmException {
        ArrayDeque<File> files;
        ArrayList<File> rootFolderList=new ArrayList<>();
        for (String rootFolder : config.getRootFolders()) {
            rootFolderList.add(new File(rootFolder));
        }
        for (File file : rootFolderList) {
            files = recursionScanFiles(file);
            fileList.addAll(files);
        }
        return fileList;
    }


    /**
     * 递归扫描文件夹下的目录
     *
     * @param folder 目录
     * @return 需要更新的文件
     */
    private ArrayDeque<File> recursionScanFiles(File folder) throws IOException, NoSuchAlgorithmException {
        File[] files = folder.listFiles();
        ArrayDeque<File> fileList = new ArrayDeque<>();
        for (File file : files) {
            if (file.isDirectory()) {
                fileList.addAll(recursionScanFiles(file));
            } else {
                curFileSet.add(file.getAbsolutePath());
                if (shouldFileUpdate(file)) {
                    fileList.add(file);
                }
            }
        }
        return fileList;
    }


    private boolean shouldFileUpdate(File file) throws IOException, NoSuchAlgorithmException {
        if (!fileMd5Map.containsKey(file.getAbsolutePath())) {
            fileMd5Map.put(file.getAbsolutePath(), FileData.getMd5(file));
            return true;
        }
        if (file.lastModified() > config.getLastUpdateTime()) {
            //这里再判断一遍md5是为了程序可靠性，因为有时文件未修改，但修改时间会发生改变
            if (!fileMd5Map.get(file.getAbsolutePath()).equals(FileData.getMd5(file))) {
                fileMd5Map.put(file.getAbsolutePath(), FileData.getMd5(file));
                return true;
            }
        }
        return false;
    }

    public Map<String, String> getFileMd5Map() {
        return fileMd5Map;
    }


    public static synchronized void startScan(FileManager manager, NetworkUtil networkUtil) throws InterruptedException, IOException, NoSuchAlgorithmException {
        //检测一下系统资源情况，你也不希望打游戏的时候突然给你来一下吧 /doge
        monitor.waitForLowUsage();
        logger.info("start get md5");
        Map<String, String> map = manager.getFileMd5Map();
        ArrayDeque<File> files = manager.scanFiles();
        logger.info("start update files");
        logger.info("files size: " + files.size());

        logger.info("files : " + files.stream().map((Function<File, Object>) File::getAbsolutePath).toList());
        while (!files.isEmpty()) {
            String path = files.pop().getAbsolutePath();
            networkUtil.upload(path, map.get(path));
        }
        logger.info("start update time");
        manager.updateLastUpdateTime(System.currentTimeMillis());

        manager.syncFileMap(networkUtil);
    }

    public void syncFileMap(NetworkUtil networkUtil) throws IOException {
        logger.info("start sync");
        Map<String,String> deletedMap=new HashMap<>();
        Set<String> keySet = fileMd5Map.keySet();
        for (String s : keySet) {
            if (!curFileSet.contains(s)) {
                deletedMap.put(s, fileMd5Map.get(s));
            }
        }
        curFileSet.clear();
        String data=networkUtil.syncFileMap(deletedMap);
        logger.info("sync data: " + deletedMap);
        fileMd5Map=gson.fromJson(data, new TypeToken<>(){}.getType());
    }

    public void start(NetworkUtil networkUtil) throws InterruptedException, IOException, NoSuchAlgorithmException {
        while (true) {
            heart(networkUtil);
            startScan(this, networkUtil);
            logger.info("start waiting");
            while (System.currentTimeMillis() <= config.getLastUpdateTime() + config.getScanIntervalTime()) {
                Thread.sleep(60000);
                heart(networkUtil);
            }
        }
    }

    private void heart(NetworkUtil networkUtil) throws InterruptedException, IOException {
        if (!networkUtil.heart()) {
            Util.showWindowsNotification("服务器连接存在问题", "请检查日志");
        }
        while (!networkUtil.heart()) {
            Thread.sleep(5000);
        }
    }
}
