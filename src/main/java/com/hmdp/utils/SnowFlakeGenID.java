package com.hmdp.utils;

import java.util.Set;
import java.util.TreeSet;

/**
 * 雪花算法生成分布式ID
 */
public class SnowFlakeGenID {
    //开始时间戳
    private static final long BEGIN_TIMESTAMP=1704067200L;
    //机器id所占位数
    private static final long machineIdBits=5L;
    //服务id所占位数
    private static final long serverIdBits=5L;
    //支持的最大机器id
    private static final long maxMachineId = ~(-1L << machineIdBits);
    //支持的最大服务id
    private static final long maxServerId = ~(-1L << serverIdBits);
    //序列在id中所占位数
    private static final long sequenceBits = 12L;
    //机器id向左移12位
    private static final long machineIdShift = sequenceBits;
    //服务id向左移17位
    private static final long serverIdShift = sequenceBits + machineIdBits;
    //时间截向左移22位
    private static final long timestampLeftShift = sequenceBits + machineIdBits + serverIdBits;
    //生成序列掩码
    private static final long sequenceMask = ~(-1L << sequenceBits);
    //机器ID(0~31)
    private long machineId;
    //数据中心ID(0~31)
    private long serverId;
    //毫秒内序列(0~4095)
    private static long sequence = 0L;
    //上次生成ID的时间截
    private static long lastTimestamp = -1L;

    public SnowFlakeGenID(long machineId, long serverId) {
        if (machineId > maxMachineId || machineId < 0) {
            throw new IllegalArgumentException(String.format("Id can't be greater than %d or less than 0", maxMachineId));
        }
        if (serverId > maxServerId || serverId < 0) {
            throw new IllegalArgumentException(String.format("Id can't be greater than %d or less than 0", maxServerId));
        }
        this.machineId = machineId;
        this.serverId = serverId;
    }

    private long generateId(long timestamp){
        //如果当前时间小于上一次ID生成的时间戳，说明系统时钟回退过这个时候应当抛出异常
        if(timestamp < lastTimestamp){
            throw new RuntimeException(
                    String.format("Clock moved backwards.  Refusing to generate id for %d milliseconds", lastTimestamp - timestamp));
        }
        //如果是同一时间生成的，则进行毫秒内序列
        if(lastTimestamp == timestamp)
        {
            sequence = (sequence + 1) & sequenceMask;
            //毫秒内序列溢出
            if(sequence == 0)
                //阻塞到下一个毫秒,获得新的时间戳
                timestamp = tilNextMillis(lastTimestamp);
        }
        else//时间戳改变，毫秒内序列重置
        {
            sequence = 0L;
        }
        //上次生成ID的时间截
        lastTimestamp = timestamp;
        return timestamp;
    }
    public synchronized long NextId() {
        long timestamp = timeGen();
        timestamp = generateId(timestamp);
        return ((timestamp - BEGIN_TIMESTAMP) << timestampLeftShift) //
                | (serverId << serverIdShift) //
                | (machineId << machineIdShift) //
                | sequence;
    }
    protected long tilNextMillis(long lastTimestamp) {
        long timestamp = timeGen();
        while (timestamp <= lastTimestamp) {
            timestamp = timeGen();
        }
        return timestamp;
    }

    protected long timeGen() {
        return System.currentTimeMillis();
    }

    public static void main(String[] args) {
        SnowFlakeGenID snowFlakeGenID = new SnowFlakeGenID(0L, 0L);
        //long类型id
        long l = snowFlakeGenID.NextId();
        System.out.println(l);
        long l1 = snowFlakeGenID.NextId();
        System.out.println(l1);
    }
}
