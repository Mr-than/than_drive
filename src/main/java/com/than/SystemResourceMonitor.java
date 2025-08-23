package com.than;

import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HWDiskStore;
import oshi.hardware.NetworkIF;

import java.util.List;

public class SystemResourceMonitor {

    /**
     * 下面几个变量分别为：预设的用户网络带宽    cpu的高占用阈值    内存高占用阈值   网络高占用阈值
     * 磁盘高占用阈值
     */
    private static final long DEFAULT_NET_SPEED = 100L * 1_000_000L;
    private static final double CPU_THRESHOLD = 80.0;
    private static final double MEM_THRESHOLD = 90.0;
    private static final double NET_THRESHOLD = 50.0;
    private static final double DISK_THRESHOLD = 50.0;

    private final SystemInfo si;
    private final CentralProcessor processor;
    private final GlobalMemory memory;
    private final List<NetworkIF> networks;
    private final List<HWDiskStore> disks;

    private long[] prevCpuTicks;
    private long[] prevNetRecv;
    private long[] prevNetSent;
    private long[] prevDiskTransferTime;

    private static final Logger logger = Logger.getLogger(SystemResourceMonitor.class);

    public SystemResourceMonitor() {
        si = new SystemInfo();
        processor = si.getHardware().getProcessor();
        memory = si.getHardware().getMemory();
        networks = si.getHardware().getNetworkIFs();
        disks = si.getHardware().getDiskStores();

        prevCpuTicks = processor.getSystemCpuLoadTicks();
        prevNetRecv = new long[networks.size()];
        prevNetSent = new long[networks.size()];
        prevDiskTransferTime = new long[disks.size()];

        for (int i = 0; i < networks.size(); i++) {
            networks.get(i).updateAttributes();
            prevNetRecv[i] = networks.get(i).getBytesRecv();
            prevNetSent[i] = networks.get(i).getBytesSent();
        }

        for (int i = 0; i < disks.size(); i++) {
            disks.get(i).updateAttributes();
            prevDiskTransferTime[i] = disks.get(i).getTransferTime();
        }
    }

    /**
     * 获取单次资源占用百分比
     * @return cpu的占用
     */
    private double getCpuUsage() {
        double cpuLoad = processor.getSystemCpuLoadBetweenTicks(prevCpuTicks) * 100;
        prevCpuTicks = processor.getSystemCpuLoadTicks();
        return cpuLoad;
    }

    /**
     * 获取单次资源占用百分比
     * @return 内存的占用
     */
    private double getMemoryUsage() {
        return (1.0 - memory.getAvailable() * 1.0 / memory.getTotal()) * 100;
    }

    /**
     * 获取单次资源占用百分比
     * @return 网络的占用
     */
    private double getNetworkUsage() {
        double netUsage = 0;
        for (int i = 0; i < networks.size(); i++) {
            NetworkIF net = networks.get(i);
            net.updateAttributes();
            long deltaRecv = net.getBytesRecv() - prevNetRecv[i];
            long deltaSent = net.getBytesSent() - prevNetSent[i];
            prevNetRecv[i] = net.getBytesRecv();
            prevNetSent[i] = net.getBytesSent();

            long speed = net.getSpeed() > 0 ? net.getSpeed() : DEFAULT_NET_SPEED;
            double usage = (deltaRecv + deltaSent) * 8.0 / speed * 100;
            netUsage = Math.max(netUsage, usage);
        }
        return netUsage;
    }

    /**
     * 获取单次资源占用百分比
     * @return 磁盘的占用
     */
    private double getDiskUsage() {
        double diskUsage = 0;
        for (int i = 0; i < disks.size(); i++) {
            HWDiskStore disk = disks.get(i);
            disk.updateAttributes();
            long deltaTransfer = disk.getTransferTime() - prevDiskTransferTime[i];
            prevDiskTransferTime[i] = disk.getTransferTime();
            double usage = Math.min((deltaTransfer / 1000.0) * 100, 100);
            diskUsage = Math.max(diskUsage, usage);
        }
        return diskUsage;
    }

    /**
     * 判断占用是否为高占用
     * @return 是否为高占用
     */
    private boolean isAnyHighUsage() {
        return getCpuUsage() > CPU_THRESHOLD ||
                getMemoryUsage() > MEM_THRESHOLD ||
                getNetworkUsage() > NET_THRESHOLD ||
                getDiskUsage() > DISK_THRESHOLD;
    }


    /**
     * 阻塞检测系统资源，如果任意资源高占用，则一直等待，
     * 直到连续三次检测都为低占用才返回
     */
    public void waitForLowUsage() throws InterruptedException {
        int consecutiveLowCount = 0;
        if (isAnyHighUsage()) {
            logger.warn("系统资源高占用，等待中...");
        }

        while (true) {
            if (isAnyHighUsage()) {
                consecutiveLowCount = 0; // 重置
            } else {
                consecutiveLowCount++;
                if (consecutiveLowCount >= 3) {
                    logger.warn("系统资源连续三次低占用，继续执行。");
                    return;
                }
            }
            Thread.sleep(1000); // 每秒检测一次
        }
    }
}
