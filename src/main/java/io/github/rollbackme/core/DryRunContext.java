package io.github.rollbackme.core;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 演习模式上下文持有者 (V2.0 引用计数版)
 * <p>
 * 使用引用计数法解决嵌套调用和线程复用时的污染问题：
 * <ul>
 *   <li>进入演习模式：计数器 +1</li>
 *   <li>退出演习模式：计数器 -1</li>
 *   <li>只有当计数器归零时，才物理清除 ThreadLocal</li>
 * </ul>
 * </p>
 * 
 * <h3>解决的问题</h3>
 * <ol>
 *   <li><strong>嵌套调用</strong>：父方法 A（已开启 DryRun）调用子方法 B，子方法 B 的计数器 +1，退出时 -1，不会误清理父方法的上下文</li>
 *   <li><strong>线程池污染</strong>：异步任务执行完后，计数器归零时自动清理，防止线程池中的线程带着 dirty flag 回到池中</li>
 * </ol>
 * 
 * <p>
 * 使用 {@link InheritableThreadLocal} 支持父子线程间的上下文传递。
 * 通过重写 {@code childValue()} 方法，确保子线程获得新的 AtomicInteger 实例（复制值但不共享引用），
 * 避免父子线程共享同一个计数器对象导致的竞态条件。
 * </p>
 * 
 * <p>
 * <strong>线程池场景：</strong>
 * 对于线程池（如 ExecutorService），InheritableThreadLocal 无法自动传递，
 * 必须配合 {@link io.github.rollbackme.support.DryRunTaskDecorator} 使用。
 * </p>
 * 
 * @author tianhaocui
 */
public class DryRunContext {
    
    /**
     * 使用 InheritableThreadLocal 支持父子线程间的上下文传递
     * <p>
     * <strong>为什么使用 InheritableThreadLocal？</strong>
     * <ul>
     *   <li>支持简单的父子线程传递（如直接 new Thread() 的场景）</li>
     *   <li>配合 TaskDecorator 使用，可以覆盖线程池场景</li>
     *   <li>更符合 Spring 框架的常见做法</li>
     * </ul>
     * </p>
     * 
     * <p>
     * <strong>如何避免竞态条件？</strong>
     * 通过重写 {@link InheritableThreadLocal#childValue(Object)} 方法，
     * 确保子线程获得的是<strong>新的 AtomicInteger 实例</strong>（复制值但不共享引用），
     * 从而避免父子线程共享同一个计数器对象导致的并发问题。
     * </p>
     * 
     * <p>
     * <strong>线程池场景：</strong>
     * 对于线程池（如 ExecutorService），InheritableThreadLocal 无法自动传递，
     * 必须配合 {@link io.github.rollbackme.support.DryRunTaskDecorator} 使用。
     * TaskDecorator 会在任务执行前手动传递上下文，并在执行后清理。
     * </p>
     * 
     * 使用 AtomicInteger 作为引用计数器：
     * - 0：非演习模式
     * - >0：演习模式，数值表示嵌套深度
     */
    private static final InheritableThreadLocal<AtomicInteger> CONTEXT = 
        new InheritableThreadLocal<AtomicInteger>() {
            @Override
            protected AtomicInteger initialValue() {
                return new AtomicInteger(0);
            }
            
            @Override
            protected AtomicInteger childValue(AtomicInteger parentValue) {
                // 关键：返回新的 AtomicInteger 实例，复制值但不共享引用
                // 这样可以避免父子线程共享同一个计数器对象导致的竞态条件
                if (parentValue != null && parentValue.get() > 0) {
                    return new AtomicInteger(parentValue.get());
                }
                return new AtomicInteger(0);
            }
        };
    
    /**
     * 进入演习模式（计数器 +1）
     * <p>
     * 每次进入 @DryRun 切面时调用，支持嵌套调用场景
     * </p>
     */
    public static void enter() {
        CONTEXT.get().incrementAndGet();
    }
    
    /**
     * 退出演习模式（计数器 -1）
     * <p>
     * 每次退出 @DryRun 切面时调用。
     * 当计数器归零时，彻底清理 ThreadLocal 资源，防止线程池污染。
     * </p>
     */
    public static void exit() {
        AtomicInteger counter = CONTEXT.get();
        if (counter.decrementAndGet() <= 0) {
            CONTEXT.remove();
        }
    }
    
    /**
     * 判断当前线程是否处于演习模式
     * 
     * @return true=演习模式（计数器 > 0），false=正常模式（计数器 = 0）
     */
    public static boolean isDryRun() {
        AtomicInteger counter = CONTEXT.get();
        return counter != null && counter.get() > 0;
    }
    
    /**
     * 强制清理当前线程的演习标识
     * <p>
     * 用于 TaskDecorator 的 finally 块，确保异步任务执行完后彻底清理上下文。
     * 注意：此方法会直接清除 ThreadLocal，不检查计数器，应谨慎使用。
     * </p>
     */
    public static void clear() {
        CONTEXT.remove();
    }
    
    /**
     * 获取当前上下文的快照（用于跨线程传递）
     * 
     * @return 当前演习状态
     */
    public static boolean snapshot() {
        return isDryRun();
    }
    
    /**
     * 恢复上下文快照（用于跨线程传递）
     * <p>
     * 在异步任务的工作线程中调用，恢复父线程的演习状态。
     * 如果 snapshot 为 true，将计数器设置为 1。
     * </p>
     * 
     * @param snapshot 要恢复的演习状态
     * @deprecated 推荐使用 {@link #enter()} 方法，TaskDecorator 已直接调用 enter()
     */
    @Deprecated
    public static void restore(boolean snapshot) {
        if (snapshot) {
            // 恢复时设置为 1，表示当前线程处于演习模式
            CONTEXT.get().set(1);
        } else {
            // 如果快照为 false，确保计数器为 0
            CONTEXT.get().set(0);
        }
    }
    
    /**
     * 设置当前线程为演习模式（兼容旧 API，内部使用 enter）
     * 
     * @param dryRun 是否开启演习模式
     * @deprecated 推荐使用 {@link #enter()} 和 {@link #exit()} 方法
     */
    @Deprecated
    public static void setDryRun(boolean dryRun) {
        if (dryRun) {
            enter();
        } else {
            exit();
        }
    }
}

