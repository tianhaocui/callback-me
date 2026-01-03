package io.github.rollbackme.config;

import io.github.rollbackme.aspect.DryRunAspect;
import io.github.rollbackme.support.DryRunTaskDecorator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;

/**
 * RollbackMe 自动装配类
 * <p>
 * 负责注册演习模式所需的核心组件：
 * <ul>
 *   <li>{@link DryRunAspect} - AOP 切面，拦截 @DryRun 注解</li>
 *   <li>{@link DryRunTaskDecorator} - 任务装饰器，支持多线程上下文传递</li>
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
     * <p><b>重要提示：</b></p>
     * <p>
     * 此 Bean 的注册不会自动应用到所有线程池。
     * 用户需要在配置线程池时手动设置 TaskDecorator，例如：
     * </p>
     * <pre>
     * &#64;Bean
     * public ThreadPoolTaskExecutor taskExecutor(DryRunTaskDecorator decorator) {
     *     ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
     *     executor.setTaskDecorator(decorator);  // 关键步骤
     *     executor.initialize();
     *     return executor;
     * }
     * </pre>
     * 
     * @return DryRunTaskDecorator 实例
     */
    @Bean
    @ConditionalOnMissingBean(TaskDecorator.class)
    public DryRunTaskDecorator dryRunTaskDecorator() {
        logger.info("[RollbackMe] 注册 DryRunTaskDecorator 任务装饰器");
        logger.info("[RollbackMe] 提示：请在线程池配置中使用 setTaskDecorator() 方法应用此装饰器");
        return new DryRunTaskDecorator();
    }
}

