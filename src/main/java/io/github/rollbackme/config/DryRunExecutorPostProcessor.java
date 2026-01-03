package io.github.rollbackme.config;

import io.github.rollbackme.support.DryRunTaskDecorator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.ArrayList;
import java.util.List;

/**
 * 线程池后置处理器
 * <p>
 * 自动为所有用户自定义的 {@link ThreadPoolTaskExecutor} 注入演习装饰器，
 * 防止用户自定义线程池漏掉演习上下文导致数据泄露。
 * </p>
 * 
 * <h3>工作原理</h3>
 * <ol>
 *   <li>检测容器中所有的 {@link ThreadPoolTaskExecutor} Bean</li>
 *   <li>如果用户已配置 {@link TaskDecorator}，使用 {@link CompositeTaskDecorator} 组合装饰器</li>
 *   <li>如果用户未配置，直接添加演习装饰器</li>
 * </ol>
 * 
 * <h3>为什么需要这个处理器？</h3>
 * <p>
 * 用户可能自定义线程池但忘记设置 TaskDecorator：
 * </p>
 * <pre>
 * &#64;Bean("myExecutor")
 * public Executor myExecutor() {
 *     ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
 *     // 忘记设置 TaskDecorator！
 *     executor.initialize();
 *     return executor;
 * }
 * </pre>
 * <p>
 * 这会导致 {@code @Async("myExecutor")} 方法无法传递演习标识，产生脏数据。
 * </p>
 * 
 * @author tianhaocui
 * @see DryRunTaskDecorator
 * @see CompositeTaskDecorator
 */
public class DryRunExecutorPostProcessor implements BeanPostProcessor {
    
    private static final Logger logger = LoggerFactory.getLogger(DryRunExecutorPostProcessor.class);
    
    private final DryRunTaskDecorator dryRunTaskDecorator;
    
    /**
     * 构造函数
     * 
     * @param dryRunTaskDecorator 演习装饰器
     */
    public DryRunExecutorPostProcessor(DryRunTaskDecorator dryRunTaskDecorator) {
        this.dryRunTaskDecorator = dryRunTaskDecorator;
    }
    
    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        // 只处理 ThreadPoolTaskExecutor
        if (bean instanceof ThreadPoolTaskExecutor) {
            ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) bean;
            TaskDecorator existingDecorator = executor.getTaskDecorator();
            
            // 如果用户已经配置了装饰器
            if (existingDecorator != null) {
                // 检查是否已经包含了我们的装饰器（避免重复添加）
                if (existingDecorator instanceof CompositeTaskDecorator) {
                    CompositeTaskDecorator composite = (CompositeTaskDecorator) existingDecorator;
                    if (composite.contains(dryRunTaskDecorator)) {
                        // 已经包含，跳过
                        logger.debug("[RollbackMe] Bean '{}' 已包含演习装饰器，跳过", beanName);
                        return bean;
                    }
                    // 添加到现有的组合装饰器中
                    composite.addDecorator(dryRunTaskDecorator);
                    logger.info("[RollbackMe] ✅ 已为 Bean '{}' 添加演习装饰器（组合模式）", beanName);
                } else if (existingDecorator == dryRunTaskDecorator) {
                    // 已经是我们的装饰器，跳过
                    logger.debug("[RollbackMe] Bean '{}' 已使用演习装饰器，跳过", beanName);
                    return bean;
                } else {
                    // 用户有自定义装饰器，使用组合模式
                    CompositeTaskDecorator composite = new CompositeTaskDecorator();
                    composite.addDecorator(existingDecorator);  // 先添加用户的
                    composite.addDecorator(dryRunTaskDecorator);  // 再添加我们的
                    executor.setTaskDecorator(composite);
                    logger.info("[RollbackMe] ✅ 已为 Bean '{}' 组合演习装饰器（保留用户装饰器）", beanName);
                }
            } else {
                // 用户没有配置装饰器，直接添加我们的
                executor.setTaskDecorator(dryRunTaskDecorator);
                logger.info("[RollbackMe] ✅ 已为 Bean '{}' 自动注入演习装饰器", beanName);
            }
        }
        
        return bean;
    }
    
    /**
     * 组合任务装饰器
     * <p>
     * 支持多个 {@link TaskDecorator} 链式组合，按顺序执行。
     * </p>
     */
    public static class CompositeTaskDecorator implements TaskDecorator {
        
        private final List<TaskDecorator> decorators = new ArrayList<>();
        
        /**
         * 添加装饰器
         * 
         * @param decorator 装饰器
         */
        public void addDecorator(TaskDecorator decorator) {
            if (decorator != null) {
                decorators.add(decorator);
            }
        }
        
        /**
         * 检查是否包含指定装饰器
         * 
         * @param decorator 装饰器
         * @return true=已包含
         */
        public boolean contains(TaskDecorator decorator) {
            return decorators.contains(decorator);
        }
        
        @Override
        public Runnable decorate(Runnable runnable) {
            Runnable result = runnable;
            // 按顺序应用所有装饰器（从后往前，因为每次装饰都会包装上一次的结果）
            for (int i = decorators.size() - 1; i >= 0; i--) {
                result = decorators.get(i).decorate(result);
            }
            return result;
        }
    }
}

