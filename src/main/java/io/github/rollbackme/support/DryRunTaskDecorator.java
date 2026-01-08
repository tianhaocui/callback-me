package io.github.rollbackme.support;

import io.github.rollbackme.core.DryRunContext;
import org.springframework.core.task.TaskDecorator;
import org.springframework.lang.NonNull;

/**
 * 线程上下文装饰器
 * <p>
 * 用于将父线程的演习状态传递给子线程，防止子线程报错导致计数器不归零。
 * </p>
 * 
 * <h3>核心安全机制</h3>
 * <ol>
 * <li><strong>快照传递</strong>：在父线程中获取 boolean 状态快照，不传递 AtomicInteger 对象</li>
 * <li><strong>父子隔离</strong>：子线程拥有独立的计数器，避免父子线程共享同一个计数器导致的竞态条件</li>
 * <li><strong>兜底清理</strong>：使用 try...finally 确保无论业务逻辑是否报错，都会清理上下文</li>
 * </ol>
 * 
 * <h3>工作原理</h3>
 * <ol>
 * <li>在父线程（提交任务时）：获取当前演习状态的 boolean 快照</li>
 * <li>在工作线程（执行任务前）：如果父线程是演习模式，调用 enter() 为子线程创建独立的计数器</li>
 * <li>在工作线程（执行任务后）：在 finally 块中强制清理，防止线程池污染</li>
 * </ol>
 * 
 * <h3>使用方式</h3>
 * 
 * <pre>
 * &#64;Configuration
 * public class AsyncConfig {
 *     &#64;Bean
 *     public ThreadPoolTaskExecutor taskExecutor(DryRunTaskDecorator decorator) {
 *         ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
 *         executor.setCorePoolSize(10);
 *         executor.setMaxPoolSize(20);
 *         executor.setTaskDecorator(decorator); // 关键：设置装饰器
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

    /**
     * 装饰任务，实现上下文传递
     * 
     * @param runnable 原始任务
     * @return 装饰后的任务
     */
    @Override
    @NonNull
    public Runnable decorate(@NonNull Runnable runnable) {
        // 1. 【在父线程】获取当前演习状态
        // 这里只拿 boolean 值，不拿 AtomicInteger 对象，实现父子隔离
        boolean isParentDryRun = DryRunContext.isDryRun();

        return () -> {
            // 2. 【在子线程】判断是否需要开启演习
            boolean needCleanup = false;
            try {
                if (isParentDryRun) {
                    // 如果父线程是演习模式，子线程也进入演习模式
                    // 这会为当前子线程创建一个全新的 AtomicInteger(1)
                    DryRunContext.enter();
                    needCleanup = true;
                }

                // 3. 执行真实的业务逻辑
                runnable.run();

            } finally {
                // 4. 【核心保护】无论业务逻辑是否报错，必须清理现场！
                if (needCleanup) {
                    // 强制退出（计数器 -1）
                    DryRunContext.exit();

                    // DryRunContext.exit() handles the counter decrement and removal when 0
                    // No need to force clear here, which would break nested scenarios
                }
            }
        };
    }
}
