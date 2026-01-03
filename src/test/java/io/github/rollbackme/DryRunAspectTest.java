package io.github.rollbackme;

import io.github.rollbackme.annotation.DryRun;
import io.github.rollbackme.aspect.DryRunAspect;
import io.github.rollbackme.config.RollbackMeProperties;
import io.github.rollbackme.core.DryRunContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * DryRunAspect 切面测试（使用 Mockito）
 * <p>
 * 测试用例 1：基本回滚测试
 * 验证当 Header 存在时，事务管理器的 rollback 方法被调用
 * </p>
 * 
 * @author tianhaocui
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class DryRunAspectTest {
    
    @Mock
    private RollbackMeProperties properties;
    
    @Mock
    private ApplicationContext applicationContext;
    
    @Mock
    private PlatformTransactionManager transactionManager;
    
    @Mock
    private ProceedingJoinPoint joinPoint;
    
    @Mock
    private MethodSignature methodSignature;
    
    @Mock
    private TransactionStatus transactionStatus;
    
    @InjectMocks
    private DryRunAspect dryRunAspect;
    
    @BeforeEach
    public void setup() {
        // 配置默认行为
        when(properties.isEnabled()).thenReturn(true);
        when(properties.getHeaderName()).thenReturn("dry-run");
        when(properties.isVerboseLogging()).thenReturn(false);
        
        // Mock 事务管理器
        when(applicationContext.getBean(PlatformTransactionManager.class))
            .thenReturn(transactionManager);
        when(transactionManager.getTransaction(any()))
            .thenReturn(transactionStatus);
        when(transactionStatus.isCompleted()).thenReturn(false);
    }
    
    @AfterEach
    public void cleanup() {
        DryRunContext.clear();
        RequestContextHolder.resetRequestAttributes();
    }
    
    /**
     * 测试用例 1：基本回滚测试
     * 验证当 HTTP Header 存在时，事务会被强制回滚
     */
    @Test
    public void testBasicRollback() throws Throwable {
        // 创建带有演习标识的 HTTP 请求
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("dry-run", "true");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        
        // Mock DryRun 注解
        DryRun dryRun = mock(DryRun.class);
        when(dryRun.transactionManager()).thenReturn("");
        // propagateToChildThread 在此测试中未使用，移除以避免 UnnecessaryStubbingException
        
        // Mock 方法签名
        Method method = TestService.class.getMethod("testMethod");
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        
        // Mock 方法执行结果
        String expectedResult = "test result";
        when(joinPoint.proceed()).thenReturn(expectedResult);
        
        // 执行切面
        Object result = dryRunAspect.around(joinPoint, dryRun);
        
        // 验证
        assertEquals(expectedResult, result);
        verify(transactionManager, times(1)).getTransaction(any());
        verify(transactionManager, times(1)).rollback(transactionStatus);
        verify(joinPoint, times(1)).proceed();
    }
    
    /**
     * 测试非演习模式（Header 不存在）
     */
    @Test
    public void testNonDryRunMode() throws Throwable {
        // 创建不带演习标识的请求
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        
        DryRun dryRun = mock(DryRun.class);
        
        String expectedResult = "test result";
        when(joinPoint.proceed()).thenReturn(expectedResult);
        
        // 执行切面
        Object result = dryRunAspect.around(joinPoint, dryRun);
        
        // 验证：应该直接执行，不开启事务
        assertEquals(expectedResult, result);
        verify(transactionManager, never()).getTransaction(any());
        verify(transactionManager, never()).rollback(any());
        verify(joinPoint, times(1)).proceed();
    }
    
    /**
     * 测试通过上下文传递的演习模式（多线程场景）
     */
    @Test
    public void testDryRunFromContext() throws Throwable {
        // 直接设置上下文（模拟从父线程传递）
        DryRunContext.setDryRun(true);
        
        // 不设置 HTTP 请求（模拟子线程场景）
        
        DryRun dryRun = mock(DryRun.class);
        when(dryRun.transactionManager()).thenReturn("");
        
        Method method = TestService.class.getMethod("testMethod");
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        
        String expectedResult = "test result";
        when(joinPoint.proceed()).thenReturn(expectedResult);
        
        // 执行切面
        Object result = dryRunAspect.around(joinPoint, dryRun);
        
        // 验证：应该检测到上下文中的演习标识，并回滚事务
        assertEquals(expectedResult, result);
        verify(transactionManager, times(1)).getTransaction(any());
        verify(transactionManager, times(1)).rollback(transactionStatus);
    }
    
    /**
     * 测试全局开关关闭的情况
     */
    @Test
    public void testDisabledGlobally() throws Throwable {
        // 关闭全局开关
        when(properties.isEnabled()).thenReturn(false);
        
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("dry-run", "true");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        
        DryRun dryRun = mock(DryRun.class);
        
        String expectedResult = "test result";
        when(joinPoint.proceed()).thenReturn(expectedResult);
        
        // 执行切面
        Object result = dryRunAspect.around(joinPoint, dryRun);
        
        // 验证：即使有 Header，也应该直接执行（因为全局开关关闭）
        assertEquals(expectedResult, result);
        verify(transactionManager, never()).getTransaction(any());
        verify(transactionManager, never()).rollback(any());
    }
    
    /**
     * 测试方法抛出异常时依然回滚
     */
    @Test
    public void testRollbackOnException() throws Throwable {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("dry-run", "true");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        
        DryRun dryRun = mock(DryRun.class);
        when(dryRun.transactionManager()).thenReturn("");
        
        Method method = TestService.class.getMethod("testMethod");
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        
        // Mock 方法执行时抛出异常
        RuntimeException exception = new RuntimeException("Test exception");
        when(joinPoint.proceed()).thenThrow(exception);
        
        // 执行切面，期望抛出异常
        assertThrows(RuntimeException.class, () -> {
            dryRunAspect.around(joinPoint, dryRun);
        });
        
        // 验证：即使抛出异常，事务依然应该被回滚
        verify(transactionManager, times(1)).getTransaction(any());
        verify(transactionManager, times(1)).rollback(transactionStatus);
    }
    
    /**
     * 测试上下文清理（防止污染）
     */
    @Test
    public void testContextCleanup() throws Throwable {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("dry-run", "true");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        
        DryRun dryRun = mock(DryRun.class);
        when(dryRun.transactionManager()).thenReturn("");
        
        Method method = TestService.class.getMethod("testMethod");
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        
        when(joinPoint.proceed()).thenReturn("test");
        
        // 执行前，上下文应该是空的
        assertFalse(DryRunContext.isDryRun());
        
        // 执行切面
        dryRunAspect.around(joinPoint, dryRun);
        
        // 执行后，上下文应该被清理
        assertFalse(DryRunContext.isDryRun(), "切面执行完毕后应该清理上下文");
    }
    
    /**
     * 测试服务类（用于 Mock）
     */
    public static class TestService {
        @DryRun
        public String testMethod() {
            return "test";
        }
    }
}

