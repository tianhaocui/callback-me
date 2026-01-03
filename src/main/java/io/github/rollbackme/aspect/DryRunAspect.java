package io.github.rollbackme.aspect;

import io.github.rollbackme.annotation.DryRun;
import io.github.rollbackme.config.RollbackMeProperties;
import io.github.rollbackme.core.DryRunContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;

/**
 * 演习模式切面
 * <p>
 * 拦截所有标注了 {@link DryRun} 注解的方法，
 * 判断当前请求是否为演习模式，如果是则开启事务并在执行后强制回滚。
 * </p>
 * 
 * <h3>判断逻辑（支持多线程）</h3>
 * <ol>
 *   <li>优先检查 {@link DryRunContext#isDryRun()}（子线程场景）</li>
 *   <li>如果为 false，再检查 HTTP Header（父线程入口场景）</li>
 *   <li>如果确认为演习模式，设置 {@link DryRunContext} 并开启事务</li>
 * </ol>
 * 
 * <h3>事务控制</h3>
 * <ul>
 *   <li>使用 {@link TransactionDefinition#PROPAGATION_REQUIRES_NEW} 开启独立事务</li>
 *   <li>无论执行成功或失败，都在 finally 块中强制回滚</li>
 *   <li>清理 {@link DryRunContext}，防止线程池污染</li>
 * </ul>
 * 
 * @author tianhaocui
 * @see DryRun
 * @see DryRunContext
 */
@Aspect
@Order(100)
public class DryRunAspect {
    
    private static final Logger logger = LoggerFactory.getLogger(DryRunAspect.class);
    
    @Autowired
    private RollbackMeProperties properties;
    
    @Autowired
    private ApplicationContext applicationContext;
    
    /**
     * 环绕通知：拦截 @DryRun 注解的方法
     * 
     * @param joinPoint 连接点
     * @param dryRun 注解实例
     * @return 方法执行结果
     * @throws Throwable 方法执行异常
     */
    @Around("@annotation(dryRun)")
    public Object around(ProceedingJoinPoint joinPoint, DryRun dryRun) throws Throwable {
        // 如果全局开关关闭，直接执行原方法
        if (!properties.isEnabled()) {
            return joinPoint.proceed();
        }
        
        // 判断是否为演习模式（支持多线程）
        boolean isDryRunMode = checkDryRunMode();
        
        if (!isDryRunMode) {
            // 非演习模式，正常执行
            return joinPoint.proceed();
        }
        
        // 演习模式：标记上下文 + 开启事务 + 强制回滚
        return executeWithDryRun(joinPoint, dryRun);
    }
    
    /**
     * 检查当前是否为演习模式（支持多线程）
     * 
     * @return true=演习模式，false=正常模式
     */
    private boolean checkDryRunMode() {
        // 1. 优先检查线程上下文（子线程场景）
        if (DryRunContext.isDryRun()) {
            if (properties.isVerboseLogging()) {
                logger.info("[DryRun] 检测到线程上下文中的演习标识（可能来自父线程）");
            }
            return true;
        }
        
        // 2. 检查 HTTP Header（父线程入口场景）
        try {
            ServletRequestAttributes attributes = 
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String headerValue = request.getHeader(properties.getHeaderName());
                
                if ("true".equalsIgnoreCase(headerValue) || "1".equals(headerValue)) {
                    if (properties.isVerboseLogging()) {
                        logger.info("[DryRun] 检测到 HTTP Header: {}={}",
                            properties.getHeaderName(), headerValue);
                    }
                    return true;
                }
            }
        } catch (Exception e) {
            // 非 Web 环境或获取 Request 失败，忽略
            if (logger.isDebugEnabled()) {
                logger.debug("[DryRun] 无法获取 HttpServletRequest，跳过 Header 检查", e);
            }
        }
        
        return false;
    }
    
    /**
     * 以演习模式执行方法：开启事务 + 强制回滚
     * 
     * @param joinPoint 连接点
     * @param dryRun 注解实例
     * @return 方法执行结果
     * @throws Throwable 方法执行异常
     */
    private Object executeWithDryRun(ProceedingJoinPoint joinPoint, DryRun dryRun) throws Throwable {
        // 获取事务管理器
        PlatformTransactionManager transactionManager = getTransactionManager(dryRun);
        if (transactionManager == null) {
            logger.warn("[DryRun] 未找到事务管理器，无法执行演习模式，将正常执行方法");
            return joinPoint.proceed();
        }
        
        // 标记当前线程为演习模式
        boolean needClearContext = !DryRunContext.isDryRun();
        if (needClearContext) {
            DryRunContext.setDryRun(true);
        }
        
        // 开启新事务（REQUIRES_NEW 确保独立事务）
        DefaultTransactionDefinition definition = new DefaultTransactionDefinition();
        definition.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        definition.setName("DryRunTransaction-" + getMethodName(joinPoint));
        TransactionStatus transactionStatus = transactionManager.getTransaction(definition);
        
        if (properties.isVerboseLogging()) {
            logger.info("[DryRun] 已开启演习事务: {}", definition.getName());
        }
        
        Object result = null;
        Throwable exception = null;
        
        try {
            // 执行目标方法
            result = joinPoint.proceed();
            return result;
            
        } catch (Throwable t) {
            exception = t;
            throw t;
            
        } finally {
            // 强制回滚事务
            try {
                if (!transactionStatus.isCompleted()) {
                    transactionManager.rollback(transactionStatus);
                    
                    if (properties.isVerboseLogging()) {
                        String status = exception != null ? "异常" : "成功";
                        logger.info("[DryRun] 演习事务已回滚（方法执行{}）: {}", 
                            status, definition.getName());
                    }
                }
            } catch (Exception e) {
                logger.error("[DryRun] 回滚事务时发生异常", e);
            }
            
            // 清理上下文（防止线程池污染）
            if (needClearContext) {
                DryRunContext.clear();
                if (logger.isDebugEnabled()) {
                    logger.debug("[DryRun] 已清理演习标识");
                }
            }
        }
    }
    
    /**
     * 获取事务管理器
     * 
     * @param dryRun 注解实例
     * @return 事务管理器
     */
    private PlatformTransactionManager getTransactionManager(DryRun dryRun) {
        String tmName = dryRun.transactionManager();
        
        try {
            if (StringUtils.hasText(tmName)) {
                // 指定了事务管理器名称
                return applicationContext.getBean(tmName, PlatformTransactionManager.class);
            } else {
                // 使用默认事务管理器
                return applicationContext.getBean(PlatformTransactionManager.class);
            }
        } catch (Exception e) {
            logger.error("[DryRun] 获取事务管理器失败: {}", tmName, e);
            return null;
        }
    }
    
    /**
     * 获取方法全名
     * 
     * @param joinPoint 连接点
     * @return 方法全名
     */
    private String getMethodName(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        return method.getDeclaringClass().getSimpleName() + "." + method.getName();
    }
}

