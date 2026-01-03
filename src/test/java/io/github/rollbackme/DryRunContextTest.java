package io.github.rollbackme;

import io.github.rollbackme.core.DryRunContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DryRunContext 单元测试（引用计数版本）
 * 
 * @author tianhaocui
 */
public class DryRunContextTest {
    
    @AfterEach
    public void cleanup() {
        DryRunContext.clear();
    }
    
    @Test
    public void testEnterAndExit() {
        // 默认应该是 false
        assertFalse(DryRunContext.isDryRun());
        
        // 进入演习模式
        DryRunContext.enter();
        assertTrue(DryRunContext.isDryRun());
        
        // 退出演习模式
        DryRunContext.exit();
        assertFalse(DryRunContext.isDryRun());
    }
    
    @Test
    public void testNestedCalls() {
        // 测试嵌套调用场景
        assertFalse(DryRunContext.isDryRun());
        
        // 第一层
        DryRunContext.enter();
        assertTrue(DryRunContext.isDryRun());
        
        // 第二层（嵌套）
        DryRunContext.enter();
        assertTrue(DryRunContext.isDryRun());
        
        // 退出第二层
        DryRunContext.exit();
        assertTrue(DryRunContext.isDryRun(), "第一层还在，应该仍然是演习模式");
        
        // 退出第一层
        DryRunContext.exit();
        assertFalse(DryRunContext.isDryRun(), "所有层都退出，应该不再是演习模式");
    }
    
    @Test
    public void testClear() {
        DryRunContext.enter();
        assertTrue(DryRunContext.isDryRun());
        
        // 强制清理
        DryRunContext.clear();
        assertFalse(DryRunContext.isDryRun());
    }
    
    @Test
    public void testSnapshot() {
        DryRunContext.enter();
        boolean snapshot = DryRunContext.snapshot();
        assertTrue(snapshot);
        
        DryRunContext.exit();
        assertFalse(DryRunContext.isDryRun());
    }
    
    @Test
    public void testRestore() {
        // 恢复为演习模式
        DryRunContext.restore(true);
        assertTrue(DryRunContext.isDryRun());
        
        // 恢复为正常模式
        DryRunContext.restore(false);
        assertFalse(DryRunContext.isDryRun());
    }
    
    @Test
    public void testInheritableThreadLocal() throws InterruptedException {
        // 在主线程进入演习模式
        DryRunContext.enter();
        assertTrue(DryRunContext.isDryRun(), "主线程应该处于演习模式");
        
        // 创建子线程（使用 Thread 直接创建，InheritableThreadLocal 会自动传递）
        final boolean[] childValue = {false};
        Thread childThread = new Thread(() -> {
            childValue[0] = DryRunContext.isDryRun();
        });
        
        childThread.start();
        childThread.join();
        
        // 验证子线程继承了父线程的值（InheritableThreadLocal 的特性）
        assertTrue(childValue[0], "子线程应该继承父线程的演习标识（InheritableThreadLocal 自动传递）");
        
        // 清理主线程
        DryRunContext.exit();
        assertFalse(DryRunContext.isDryRun(), "主线程应该已退出演习模式");
    }
    
    @Test
    public void testDeprecatedSetDryRun() {
        // 测试向后兼容的 API
        assertFalse(DryRunContext.isDryRun());
        
        DryRunContext.setDryRun(true);
        assertTrue(DryRunContext.isDryRun());
        
        DryRunContext.setDryRun(false);
        assertFalse(DryRunContext.isDryRun());
    }
}

