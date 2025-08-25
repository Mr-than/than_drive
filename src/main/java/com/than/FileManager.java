package com.than;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.function.Function;

/**
 * 这个类是整个程序基础功能最核心的类，操作文件，控制上传等
 */
public class FileManager {

    public static String configFileName = "config.json";
    private static Config config;

    //这个队列会在每次扫描完成后被填充，内容是发生了变化或新加入的文件的绝对路径
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

    //为了在pc忙碌时，此程序尽量不要影响前台程序或高资源消耗程序的运行，此程序则需要监控资源使用情况，这个对象提供此功能
    private static SystemResourceMonitor monitor = new SystemResourceMonitor();


    public FileManager() throws IOException {
        gson = new Gson();
        config = getConfig();
        fileList = new ArrayDeque<>();
        curFileSet =new HashSet<>();
    }

    /**
     * md5 map记录了上一次上传的文件的md5，其中key是文件绝对路径，value是文件md5。这个值需要在每次程序启动时从后端获取
     * 为什么需要从后端获取，因为这个值在后端是长期存在在内存中的（为了记录当前已经上传过的文件），因此本地没有必要单独使用文件将其持久化
     * 并且每次启动程序先获取这个文件还能检测后端是否在线
     * @param map map的json
     */
    public void setFileMd5Map(String map) {
        this.fileMd5Map = gson.fromJson(map, new TypeToken<>() {
        });
        fileMd5Map = (fileMd5Map == null) ? new HashMap<>() : fileMd5Map;
    }

    /**
     * 从本地文件中获取配置文件，没有则生成
     * @return 返回配置文件实体
     * @throws IOException io发生异常时
     */
    public static Config getConfig() throws IOException {
        if (config != null) {
            return config;
        }
        readConfig();
        return config;
    }

    /**
     * 更新配置文件中的 上次更新时间
     * @param lastUpdateTime 要更新到的值
     * @throws IOException 写配置文件时的io异常
     */
    public void updateLastUpdateTime(long lastUpdateTime) throws IOException {
        config = getConfig();
        config.setLastUpdateTime(lastUpdateTime);
        writeToConfig(config);
    }

    /**
     * 将更新后的配置写入到配置文件中，这个函数主要是用于守护程序自己更新
     * @param config 更新后的配置文件实体
     * @throws IOException 写入配置文件时的io异常
     */
    private static void writeToConfig(Config config) throws IOException {
        File file = new File("./" + configFileName);
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(file))) {
            bw.write(gson.toJson(config));
        }
    }

    /**
     * 将更新后的配置写入到配置文件中，这个函数主要是用于ui更新
     * @param configText 更新后的配置文件实体json
     * @throws IOException 写入配置文件时的io异常
     */
    public static void writeToConfig(String configText) throws IOException {
        Config config = gson.fromJson(configText, Config.class);
        if (config != null) {
            writeToConfig(config);
            FileManager.config=config;
        }
    }

    /**
     * 读取配置文件
     * @throws IOException 读取时的io异常
     */
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
    public ArrayDeque<File> scanFiles() throws IOException, NoSuchAlgorithmException, InterruptedException {
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
     * @param folder 目录
     * @return 需要更新的文件
     */
    private ArrayDeque<File> recursionScanFiles(File folder) throws IOException, NoSuchAlgorithmException, InterruptedException {
        File[] files = folder.listFiles();
        ArrayDeque<File> fileList = new ArrayDeque<>();
        for (File file : files) {
            if (file.isDirectory()) {
                monitor.waitForLowUsage();
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


    /**
     * 判断文件是否需要上传
     * @param file 目标文件对象
     * @return 是否需要上传，true为需要，false则不需要
     * @throws IOException 读取目标文件时的io异常
     * @throws NoSuchAlgorithmException 获取md5函数的算法异常
     */
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

    /**
     * 外部获取md5 map
     * @return md5 map
     */
    public Map<String, String> getFileMd5Map() {
        return fileMd5Map;
    }


    /**
     * 开始扫描并上传
     * @param networkUtil 网络工具
     * @throws InterruptedException 需要Thread.sleep，发生问题时抛出异常
     * @throws IOException 网络io异常
     * @throws NoSuchAlgorithmException md5工具异常
     */
    public synchronized void startScan(NetworkUtil networkUtil) throws InterruptedException, IOException, NoSuchAlgorithmException {
        //检测一下系统资源情况，你也不希望打游戏的时候突然给你来一下吧 /doge
        monitor.waitForLowUsage();
        logger.info("start get md5");
        Map<String, String> map = getFileMd5Map();
        ArrayDeque<File> files = scanFiles();
        logger.info("start update files");
        logger.info("files size: " + files.size());
        logger.info("files : " + files.stream().map((Function<File, Object>) File::getAbsolutePath).toList());
        while (!files.isEmpty()) {
            String path = files.pop().getAbsolutePath();
            monitor.waitForLowUsage();
            networkUtil.upload(path, map.get(path));
        }
        logger.info("start update time");
        updateLastUpdateTime(System.currentTimeMillis());
        syncFileMap(networkUtil);
    }

    /**
     * 同步文件已经删除的文件
     * @param networkUtil 网络工具
     * @throws IOException 网络异常
     */
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

    /**
     * 开始运行主要功能
     * @param networkUtil 网络工具，网络工具和FileManager是循环依赖的，所以需要时才注入
     * @throws InterruptedException Thread.sleep(60000)发生问题时
     * @throws IOException 网络或文件io
     * @throws NoSuchAlgorithmException md5获取时
     */
    public void start(NetworkUtil networkUtil) throws InterruptedException, IOException, NoSuchAlgorithmException {
        while (true) {
            heart(networkUtil);
            startScan(networkUtil);
            logger.info("start waiting");
            while (System.currentTimeMillis() <= config.getLastUpdateTime() + config.getScanIntervalTime()) {
                Thread.sleep(60000);
                heart(networkUtil);
            }
        }
    }

    /**
     * 心跳函数
     * @param networkUtil 网络工具
     * @throws InterruptedException Thread.sleep发生问题时
     * @throws IOException 网络或文件io
     */
    private void heart(NetworkUtil networkUtil) throws InterruptedException, IOException {
        if (networkUtil.heart()) {
            Util.showWindowsNotification("服务器连接存在问题", "请检查日志");
        }
        while (networkUtil.heart()) {
            Thread.sleep(5000);
        }
    }
}
