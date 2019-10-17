package com.h2t.study.monitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @Description: 线程池监控类
 *
 * @Author: hetiantian
 * @Date:2019/10/17 21:30 
 * @Version: 1.0
 */
public class ThreadPoolMonitor extends ThreadPoolExecutor {
    private final Logger LOGGER = LoggerFactory.getLogger(getClass());

    /**
     * ActiveCount
     * */
    int ac = 0;

    /**
     * 当前所有线程消耗的时间
     * */
    private AtomicLong totalCostTime = new AtomicLong();

    /**
     * 当前执行的线程总数
     * */
    private AtomicLong totalTasks = new AtomicLong();

    /**
     * 线程池名称
     */
    private String poolName;

    /**
     * 最短 执行时间
     * */
    private long minCostTime;

    /**
     * 最长执行时间
     * */
    private long maxCostTime;


    /**
     * 保存任务开始执行的时间
     */
    private ThreadLocal<Long> startTime = new ThreadLocal<>();

    public ThreadPoolMonitor(int corePoolSize, int maximumPoolSize, long keepAliveTime,
                             TimeUnit unit, BlockingQueue<Runnable> workQueue, String poolName) {
        this(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue,
            Executors.defaultThreadFactory(), poolName);
    }

    public ThreadPoolMonitor(int corePoolSize, int maximumPoolSize, long keepAliveTime,
                             TimeUnit unit, BlockingQueue<Runnable> workQueue,
                             ThreadFactory threadFactory, String poolName) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory);
        this.poolName = poolName;
    }

    public static ExecutorService newFixedThreadPool(int nThreads, String poolName) {
        return new ThreadPoolMonitor(nThreads, nThreads, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), poolName);
    }

    public static ExecutorService newCachedThreadPool(String poolName) {
        return new ThreadPoolMonitor(0, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS, new SynchronousQueue<>(), poolName);
    }

    public static ExecutorService newSingleThreadExecutor(String poolName) {
        return new ThreadPoolMonitor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), poolName);
    }

    /**
     * 线程池延迟关闭时（等待线程池里的任务都执行完毕），统计线程池情况
     */
    @Override
    public void shutdown() {
        // 统计已执行任务、正在执行任务、未执行任务数量
        LOGGER.info("{} Going to shutdown. Executed tasks: {}, Running tasks: {}, Pending tasks: {}",
            this.poolName, this.getCompletedTaskCount(), this.getActiveCount(), this.getQueue().size());
        super.shutdown();
    }

    /**
     * 线程池立即关闭时，统计线程池情况
     */
    @Override
    public List<Runnable> shutdownNow() {
        // 统计已执行任务、正在执行任务、未执行任务数量
        LOGGER.info("{} Going to immediately shutdown. Executed tasks: {}, Running tasks: {}, Pending tasks: {}",
            this.poolName, this.getCompletedTaskCount(), this.getActiveCount(), this.getQueue().size());
        return super.shutdownNow();
    }

    /**
     * 任务执行之前，记录任务开始时间
     */
    @Override
    protected void beforeExecute(Thread t, Runnable r) {
        startTime.set(System.currentTimeMillis());
    }

    /**
     * 任务执行之后，计算任务结束时间
     */
    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        long costTime = System.currentTimeMillis() - startTime.get();
        //设置最大最小执行时间
        maxCostTime = maxCostTime > costTime ? maxCostTime : costTime;
        minCostTime = minCostTime <costTime ? minCostTime : costTime;
        totalCostTime.addAndGet(costTime);
        totalTasks.incrementAndGet();
        startTime.remove();  //删除，避免占用太多内存

        // 统计任务耗时、初始线程数、核心线程数、正在执行的任务数量、
        // 已完成任务数量、任务总数、队列里缓存的任务数量、池中存在的最大线程数、
        // 最大允许的线程数、线程空闲时间、线程池是否关闭、线程池是否终止
//        LOGGER.info("{}-pool-monitor: " +
//                "Duration: {} ms, PoolSize: {}, CorePoolSize: {}, ActiveCount: {}, " +
//                "Completed: {}, Task: {}, Queue: {}, LargestPoolSize: {}, " +
//                "MaximumPoolSize: {},  KeepAliveTime: {}, isShutdown: {}, isTerminated: {}",
//            this.poolName,
//            diff, this.getPoolSize(), this.getCorePoolSize(), super.getActiveCount(),
//            super.getCompletedTaskCount(), this.getTaskCount(), this.getQueue().size(), this.getLargestPoolSize(),
//            this.getMaximumPoolSize(), this.getKeepAliveTime(TimeUnit.MILLISECONDS), this.isShutdown(), this.isTerminated());

        ac = this.getActiveCount();
        LOGGER.info("CorePoolSize: {}, ActiveCount: {}",
             this.getCorePoolSize(), this.getActiveCount());
    }

    public int getAc() {
        return ac;
    }

    /**
     * 线程平均耗时
     *
     * @return
     * */
    public float getAverageCostTime() {
        return totalCostTime.get() / totalTasks.get();
    }

    /**
     * 线程最大耗时
     * */
    public long getMaxCostTime() {
        return maxCostTime;
    }

    /**
     * 线程最小耗时
     * */
    public long getMinCostTime() {
        return minCostTime;
    }

    /**
     * 生成线程池所用的线程，只是改写了线程池默认的线程工厂，传入线程池名称，便于问题追踪
     */
    static class EventThreadFactory implements ThreadFactory {
        private static final AtomicInteger poolNumber = new AtomicInteger(1);
        private final ThreadGroup group;
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;

        /**
         * 初始化线程工厂
         *
         * @param poolName 线程池名称
         */
        EventThreadFactory(String poolName) {
            SecurityManager s = System.getSecurityManager();
            group = Objects.nonNull(s) ? s.getThreadGroup() : Thread.currentThread().getThreadGroup();
            namePrefix = poolName + "-pool-" + poolNumber.getAndIncrement() + "-thread-";
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(group, r, namePrefix + threadNumber.getAndIncrement(), 0);
            if (t.isDaemon())
                t.setDaemon(false);
            if (t.getPriority() != Thread.NORM_PRIORITY)
                t.setPriority(Thread.NORM_PRIORITY);
            return t;
        }
    }
}
