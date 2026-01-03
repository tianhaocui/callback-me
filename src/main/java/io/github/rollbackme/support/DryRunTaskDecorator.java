package io.github.rollbackme.support;

import io.github.rollbackme.core.DryRunContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskDecorator;

/**
 * 演习模式任务装饰器
 * <p>
 * 这是解决多线程/异步场景下上下文传递的关键组件。
 * 通过装饰 Runnable，在任务执行前将父线程的演习标识传递到子线程，
 * 并在任务执行后清理上下文，防止线程池污染。
 * </p>
 * 
 * <h3>工作原理</h3>
 * <ol>
 *   <li>在主线程（提交任务时）：捕获当前的演习状态快照</li>
 *   <li>在工作线程（执行任务前）：恢复演习状态到当前线程</li>
 *   <li>在工作线程（执行任务后）：清理演习状态，避免污染线程池</li>
 * </ol>
 * 
 * <h3>使用方式</h3>
 * <pre>
 * &#64;Configuration
 * public class AsyncConfig {
 *     &#64;Bean
 *     public ThreadPoolTaskExecutor taskExecutor(DryRunTaskDecorator decorator) {
 *         ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
 *         executor.setCorePoolSize(10);
 *         executor.setMaxPoolSize(20);
 *         executor.setTaskDecorator(decorator);  // 关键：设置装饰器
 *         executor.initialize();
 *         return executor;
 *     }
 * }
 * </pre>
 * 
 * @author tianhaocui
 * @see DryRunContext
 */
public class DryRunTaskDecorator implements TaskDecorator {
    
    private static final Logger logger = LoggerFactory.getLogger(DryRunTaskDecorator.class);
    
    /**
     * 装饰任务，实现上下文传递
     * 
     * @param runnable 原始任务
     * @return 装饰后的任务
     */
    @Override
    public Runnable decorate(Runnable runnable) {
        // 在提交任务的线程（通常是主线程）中，捕获当前的演习状态
        boolean dryRunSnapshot = DryRunContext.snapshot();
        
        if (dryRunSnapshot && logger.isDebugEnabled()) {
            logger.debug("[DryRun] 捕获演习标识，准备传递到异步任务");
        }
        
        // 返回装饰后的 Runnable
        return () -> {
            // 在执行任务的线程（工作线程）中，恢复演习状态
            if (dryRunSnapshot) {
                DryRunContext.restore(dryRunSnapshot);
                if (logger.isDebugEnabled()) {
                    logger.debug("[DryRun] 已在工作线程中恢复演习标识");
                }
            }
            
            try {
                // 执行原始任务
                runnable.run();
            } finally {
                // 清理上下文，避免污染线程池中的线程
                if (dryRunSnapshot) {
                    DryRunContext.clear();
                    if (logger.isDebugEnabled()) {
                        logger.debug("[DryRun] 已清理工作线程的演习标识");
                    }
                }
            }
        };
    }
}

