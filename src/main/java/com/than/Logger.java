package com.than;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.locks.ReentrantLock;

public class Logger {
    public enum Level {
        DEBUG, INFO, WARN, ERROR
    }

    private static Level currentLevel = Level.INFO;
    private static String logFilePath = "log.txt";
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private final Class<?> clazz;
    private static final ReentrantLock fileLock = new ReentrantLock();

    private Logger(Class<?> clazz) {
        this.clazz = clazz;
    }

    public static Logger getLogger(Class<?> clazz) {
        return new Logger(clazz);
    }

    public static void setLevel(Level level) {
        currentLevel = level;
    }

    public static void setLogFilePath(String path) {
        logFilePath = path;
    }

    public void debug(String message) {
        log(Level.DEBUG, message, null);
    }

    public void info(String message) {
        log(Level.INFO, message, null);
    }

    public void warn(String message) {
        log(Level.WARN, message, null);
    }

    public void error(String message, Throwable throwable) {
        log(Level.ERROR, message, throwable);
    }

    private void log(Level level, String message, Throwable throwable) {
        if (level.ordinal() < currentLevel.ordinal()) {
            return;
        }

        String timestamp = dateFormat.format(new Date());
        String className = clazz.getSimpleName();
        String logMessage = String.format("[%s] [%s] [%s] %s",
                timestamp, level, className, message);

        printToConsole(level, logMessage);

        printToFile(logMessage);

        if (throwable != null) {
            throwable.printStackTrace();
            printStackTraceToFile(throwable);
        }
    }

    private void printToConsole(Level level, String message) {
        if (level == Level.ERROR) {
            System.err.println(message);
        } else {
            System.out.println(message);
        }
    }

    private void printToFile(String message) {
        fileLock.lock();
        try (FileWriter fw = new FileWriter(logFilePath, true);
             PrintWriter pw = new PrintWriter(fw)) {
            pw.println(message);
        } catch (IOException e) {
            System.err.println("日志写入文件失败: " + e.getMessage());
        } finally {
            fileLock.unlock();
        }
    }

    private void printStackTraceToFile(Throwable throwable) {
        fileLock.lock();
        try (FileWriter fw = new FileWriter(logFilePath, true);
             PrintWriter pw = new PrintWriter(fw)) {
            throwable.printStackTrace(pw);
        } catch (IOException e) {
            System.err.println("异常堆栈写入文件失败: " + e.getMessage());
        } finally {
            fileLock.unlock();
        }
    }
}
