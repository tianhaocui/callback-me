package io.github.rollbackme.config;

import io.github.rollbackme.aspect.DryRunAspect;
import io.github.rollbackme.support.DryRunTaskDecorator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * RollbackMe 自动装配类
 * <p>
 * 负责注册演习模式所需的核心组件：
 * <ul>
 *   <li>{@link DryRunAspect} - AOP 切面，拦截 @DryRun 注解</li>
 *   <li>{@link DryRunTaskDecorator} - 任务装饰器，支持多线程上下文传递</li>
 *   <li>{@link RollbackMeAsyncConfigurer} - 自动配置异步执行器（可选）</li>
 * </ul>
 * </p>
 * 
 * @author tianhaocui
 */
@Configuration
@EnableConfigurationProperties(RollbackMeProperties.class)
@ConditionalOnProperty(
    prefix = "rollback-me",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class RollbackMeAutoConfiguration {
    
    private static final Logger logger = LoggerFactory.getLogger(RollbackMeAutoConfiguration.class);
    
    public RollbackMeAutoConfiguration() {
        logger.info("[RollbackMe] 演习模式自动装配已启用");
    }
    
    /**
     * 注册演习模式切面
     * <p>
     * 负责拦截 @DryRun 注解，实现事务演习和强制回滚
     * </p>
     * 
     * @return DryRunAspect 实例
     */
    @Bean
    public DryRunAspect dryRunAspect() {
        logger.info("[RollbackMe] 注册 DryRunAspect 切面");
        return new DryRunAspect();
    }
    
    /**
     * 注册演习模式任务装饰器
     * <p>
     * 用于在异步任务中传递演习标识。
     * 如果容器中已存在 TaskDecorator，则不注册此 Bean（避免冲突）。
     * </p>
     * 
     * @return DryRunTaskDecorator 实例
     */
    @Bean
    @ConditionalOnMissingBean(TaskDecorator.class)
    public DryRunTaskDecorator dryRunTaskDecorator() {
        logger.info("[RollbackMe] 注册 DryRunTaskDecorator 任务装饰器");
        return new DryRunTaskDecorator();
    }
    
    /**
     * 【核心修复】独立的异步配置内部类
     * <p>
     * 只有当容器中不存在 {@link AsyncConfigurer} 时，才会启用这个配置。
     * 这样既保证了默认情况下的 @Async 上下文传递，又不影响用户自定义的线程池配置。
     * </p>
     * 
     * <p>
     * <b>工作原理：</b>
     * <ol>
     *   <li>如果用户自定义了 {@link AsyncConfigurer}，Spring 会跳过此配置，使用用户的配置</li>
     *   <li>如果用户没有自定义，Spring 会加载此配置，让 {@code @Async} 自动具备演习能力</li>
     *   <li>使用 {@link ObjectProvider} 注入装饰器，防止循环依赖或 Bean 未初始化</li>
     * </ol>
     * </p>
     */
    @Configuration
    @ConditionalOnMissingBean(AsyncConfigurer.class)
    @EnableAsync
    public static class RollbackMeAsyncConfigurer implements AsyncConfigurer {
        
        private static final Logger logger = LoggerFactory.getLogger(RollbackMeAsyncConfigurer.class);
        
        private final DryRunTaskDecorator dryRunTaskDecorator;
        
        /**
         * 使用 ObjectProvider 注入，防止循环依赖或 Bean 未初始化
         * 
         * @param decoratorProvider TaskDecorator 提供者
         */
        public RollbackMeAsyncConfigurer(ObjectProvider<DryRunTaskDecorator> decoratorProvider) {
            this.dryRunTaskDecorator = decoratorProvider.getIfAvailable();
        }
        
        @Override
        public Executor getAsyncExecutor() {
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            // 默认参数配置
            executor.setCorePoolSize(10);
            executor.setMaxPoolSize(50);
            executor.setQueueCapacity(1000);
            executor.setThreadNamePrefix("rollback-me-async-");
            executor.setWaitForTasksToCompleteOnShutdown(true);
            executor.setAwaitTerminationSeconds(60);
            
            // 关键：绑定 Decorator
            if (dryRunTaskDecorator != null) {
                executor.setTaskDecorator(dryRunTaskDecorator);
                logger.info("[RollbackMe] ✅ 已激活默认异步线程池，并绑定演习上下文装饰器");
                logger.info("[RollbackMe] 提示：@Async 方法现在会自动继承演习标识，无需手动配置");
            } else {
                logger.warn("[RollbackMe] ⚠️ 未找到 TaskDecorator，异步任务无法传递演习状态");
            }
            
            executor.initialize();
            return executor;
        }
        
        @Override
        public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
            return (ex, method, params) -> {
                logger.error("[RollbackMe] 异步任务执行异常: {}.{}", 
                    method.getDeclaringClass().getName(), method.getName(), ex);
            };
        }
    }
}

