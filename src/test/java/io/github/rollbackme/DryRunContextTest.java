package io.github.rollbackme;

import io.github.rollbackme.core.DryRunContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DryRunContext 单元测试
 * 
 * @author tianhaocui
 */
public class DryRunContextTest {
    
    @AfterEach
    public void cleanup() {
        DryRunContext.clear();
    }
    
    @Test
    public void testSetAndGet() {
        // 默认应该是 false
        assertFalse(DryRunContext.isDryRun());
        
        // 设置为 true
        DryRunContext.setDryRun(true);
        assertTrue(DryRunContext.isDryRun());
        
        // 设置为 false
        DryRunContext.setDryRun(false);
        assertFalse(DryRunContext.isDryRun());
    }
    
    @Test
    public void testClear() {
        DryRunContext.setDryRun(true);
        assertTrue(DryRunContext.isDryRun());
        
        DryRunContext.clear();
        assertFalse(DryRunContext.isDryRun());
    }
    
    @Test
    public void testSnapshot() {
        DryRunContext.setDryRun(true);
        boolean snapshot = DryRunContext.snapshot();
        assertTrue(snapshot);
        
        DryRunContext.clear();
        assertFalse(DryRunContext.isDryRun());
    }
    
    @Test
    public void testRestore() {
        boolean snapshot = true;
        DryRunContext.restore(snapshot);
        assertTrue(DryRunContext.isDryRun());
        
        DryRunContext.restore(false);
        assertFalse(DryRunContext.isDryRun());
    }
    
    @Test
    public void testInheritableThreadLocal() throws InterruptedException {
        // 在主线程设置为 true
        DryRunContext.setDryRun(true);
        
        // 创建子线程（使用 Thread 直接创建，可以继承）
        final boolean[] childValue = {false};
        Thread childThread = new Thread(() -> {
            childValue[0] = DryRunContext.isDryRun();
        });
        
        childThread.start();
        childThread.join();
        
        // 验证子线程继承了父线程的值
        assertTrue(childValue[0], "子线程应该继承父线程的演习标识");
    }
}

