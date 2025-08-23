package com.than;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
public class Base {
    private static final Logger logger = Logger.getLogger(Base.class);

    /**
     * 自动注册开机启动任务
     */
    public static void autoBoot() throws URISyntaxException, IOException, InterruptedException {
        String taskName = "ThanDrive";

        String currentPath = getCurrentPath();      // Jar 或 exe 路径
        String currentDir = new File(currentPath).getParent();

        if (isTaskExists(taskName)) {
            System.out.println("任务计划已存在，直接启动程序。");
        } else {
            System.out.println("首次注册任务计划。");
            if (!isAdmin()) {
                restartAsAdmin(currentPath);
                System.exit(0);
            } else {
                createTaskWithStartIn(taskName, currentPath, currentDir);
            }
        }
    }

    /**
     * 获取当前运行文件路径（Jar 或 exe）
     */
    private static String getCurrentPath() throws URISyntaxException {
        return new File(Base.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI())
                .getPath().replace("/", "\\");
    }

    /**
     * 判断是否以管理员权限运行
     */
    private static boolean isAdmin() {
        try {
            Process process = Runtime.getRuntime().exec("net session");
            process.waitFor();
            return process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 判断任务计划是否已存在
     */
    private static boolean isTaskExists(String taskName) throws InterruptedException, IOException {
        Process process = Runtime.getRuntime().exec("schtasks /query /tn \"" + taskName + "\"");
        process.waitFor();
        java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(process.getInputStream(), "GBK"));
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.contains(taskName)) return true;
        }
        return false;
    }

    /**
     * 以管理员权限重启程序
     * 兼容 Jar 和 exe
     */
    private static void restartAsAdmin(String currentPath) throws IOException {
        File f = new File(currentPath);
        String cmd;
        if (f.getName().endsWith(".exe")) {
            cmd = "powershell Start-Process \"" + currentPath + "\" -Verb RunAs";
        } else if (f.getName().endsWith(".jar")) {
            String javaPath = System.getProperty("java.home") + "\\bin\\javaw.exe";
            cmd = "powershell Start-Process \"" + javaPath + "\" -ArgumentList '-jar \\\"" + currentPath + "\\\"' -Verb RunAs";
        } else {
            cmd = "powershell Start-Process \"" + currentPath + "\" -Verb RunAs";
        }
        Runtime.getRuntime().exec(cmd);
        logger.warn("程序需要管理员权限，已尝试以管理员身份重启。");
    }

    /**
     * 创建任务计划并设置工作目录
     */
    private static void createTaskWithStartIn(String taskName, String currentPath, String currentDir) throws IOException, InterruptedException {
        String xmlPath = currentDir + "\\task_temp.xml";

        String xmlContent = "<?xml version=\"1.0\" encoding=\"UTF-16\"?>\n" +
                "<Task version=\"1.4\" xmlns=\"http://schemas.microsoft.com/windows/2004/02/mit/task\">\n" +
                "  <RegistrationInfo>\n" +
                "    <Description>" + taskName + "</Description>\n" +
                "  </RegistrationInfo>\n" +
                "  <Triggers>\n" +
                "    <LogonTrigger>\n" +
                "      <Enabled>true</Enabled>\n" +
                "    </LogonTrigger>\n" +
                "  </Triggers>\n" +
                "  <Principals>\n" +
                "    <Principal id=\"Author\">\n" +
                "      <RunLevel>HighestAvailable</RunLevel>\n" +
                "      <UserId>S-1-5-18</UserId>\n" + // SYSTEM 用户
                "    </Principal>\n" +
                "  </Principals>\n" +
                "  <Settings>\n" +
                "    <MultipleInstancesPolicy>IgnoreNew</MultipleInstancesPolicy>\n" +
                "    <AllowStartOnDemand>true</AllowStartOnDemand>\n" +
                "    <Enabled>true</Enabled>\n" +
                "  </Settings>\n" +
                "  <Actions Context=\"Author\">\n" +
                "    <Exec>\n" +
                "      <Command>" + currentPath + "</Command>\n" +
                "      <WorkingDirectory>" + currentDir + "</WorkingDirectory>\n" +
                "    </Exec>\n" +
                "  </Actions>\n" +
                "</Task>";

        Files.write(Paths.get(xmlPath), xmlContent.getBytes());

        Process create = Runtime.getRuntime().exec(
                "schtasks /create /tn \"" + taskName + "\" /xml \"" + xmlPath + "\" /f");
        create.waitFor();

        Files.delete(Paths.get(xmlPath));
        logger.info("任务计划创建完成, exitCode=" + create.exitValue());
    }
}
