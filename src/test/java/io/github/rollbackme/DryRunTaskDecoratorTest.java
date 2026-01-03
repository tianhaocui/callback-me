package io.github.rollbackme;

import io.github.rollbackme.core.DryRunContext;
import io.github.rollbackme.support.DryRunTaskDecorator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DryRunTaskDecorator 多线程传递测试
 * <p>
 * 这是验证多线程场景下演习标识正确传递的关键测试
 * </p>
 * 
 * @author tianhaocui
 */
public class DryRunTaskDecoratorTest {
    
    private final DryRunTaskDecorator decorator = new DryRunTaskDecorator();
    
    @AfterEach
    public void cleanup() {
        DryRunContext.clear();
    }
    
    /**
     * 测试用例 2：多线程传递
     * 验证 DryRunTaskDecorator 能够将演习标识从主线程传递到工作线程
     */
    @Test
    public void testPropagateToChildThread() throws InterruptedException {
        // 在主线程设置演习标识（使用新的引用计数 API）
        DryRunContext.enter();
        assertTrue(DryRunContext.isDryRun(), "主线程应该处于演习模式");
        
        // 使用 CountDownLatch 等待异步任务完成
        CountDownLatch latch = new CountDownLatch(1);
        final boolean[] childThreadValue = {false};
        
        // 创建原始任务
        Runnable originalTask = () -> {
            try {
                // 在工作线程中检查演习标识
                childThreadValue[0] = DryRunContext.isDryRun();
            } finally {
                latch.countDown();
            }
        };
        
        // 使用装饰器包装任务
        Runnable decoratedTask = decorator.decorate(originalTask);
        
        // 在新线程中执行装饰后的任务（模拟线程池）
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(decoratedTask);
        
        // 等待任务完成
        assertTrue(latch.await(5, TimeUnit.SECONDS), "任务应该在 5 秒内完成");
        
        // 验证工作线程继承了演习标识
        assertTrue(childThreadValue[0], "工作线程应该继承主线程的演习标识");
        
        // 清理主线程上下文
        DryRunContext.exit();
        
        executor.shutdown();
    }
    
    /**
     * 测试非演习模式下的传递
     */
    @Test
    public void testNonDryRunMode() throws InterruptedException {
        // 主线程不设置演习标识（默认为 false）
        assertFalse(DryRunContext.isDryRun());
        
        CountDownLatch latch = new CountDownLatch(1);
        final boolean[] childThreadValue = {true}; // 初始化为 true，看是否被正确设置为 false
        
        Runnable originalTask = () -> {
            try {
                childThreadValue[0] = DryRunContext.isDryRun();
            } finally {
                latch.countDown();
            }
        };
        
        Runnable decoratedTask = decorator.decorate(originalTask);
        
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(decoratedTask);
        
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        
        // 验证工作线程也不处于演习模式
        assertFalse(childThreadValue[0], "工作线程应该保持非演习模式");
        
        executor.shutdown();
    }
    
    /**
     * 测试上下文清理（防止线程池污染）
     */
    @Test
    public void testContextCleanup() throws InterruptedException {
        DryRunContext.enter();
        
        CountDownLatch latch = new CountDownLatch(1);
        
        Runnable originalTask = () -> {
            // 任务执行中应该处于演习模式
            assertTrue(DryRunContext.isDryRun());
            latch.countDown();
        };
        
        Runnable decoratedTask = decorator.decorate(originalTask);
        
        // 使用固定线程池，方便后续验证线程池清理
        ExecutorService executor = Executors.newFixedThreadPool(1);
        executor.submit(decoratedTask);
        
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        
        // 再次提交一个任务到同一个线程池
        CountDownLatch latch2 = new CountDownLatch(1);
        final boolean[] secondTaskValue = {true};
        
        // 主线程清除演习标识
        DryRunContext.exit();
        
        Runnable secondTask = () -> {
            try {
                // 第二个任务不应该受到第一个任务的影响
                secondTaskValue[0] = DryRunContext.isDryRun();
            } finally {
                latch2.countDown();
            }
        };
        
        // 注意：第二个任务不使用装饰器，直接执行
        executor.submit(secondTask);
        
        assertTrue(latch2.await(5, TimeUnit.SECONDS));
        
        // 验证线程池中的线程已经被正确清理
        assertFalse(secondTaskValue[0], "线程池中的线程应该已被清理，不应该残留演习标识");
        
        executor.shutdown();
    }
}

