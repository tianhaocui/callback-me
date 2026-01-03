package io.github.rollbackme.core;

/**
 * 演习模式上下文持有者
 * <p>
 * 使用 InheritableThreadLocal 支持父子线程间的上下文传递
 * 配合 DryRunTaskDecorator 可以在异步场景下正确传递演习标识
 * </p>
 * 
 * @author tianhaocui
 */
public class DryRunContext {
    
    /**
     * 使用 InheritableThreadLocal 支持简单的父子线程传递
     * 对于线程池场景，需要配合 DryRunTaskDecorator 使用
     */
    private static final InheritableThreadLocal<Boolean> DRY_RUN_HOLDER = 
        new InheritableThreadLocal<Boolean>() {
            @Override
            protected Boolean initialValue() {
                return Boolean.FALSE;
            }
            
            @Override
            protected Boolean childValue(Boolean parentValue) {
                // 父线程的值传递给子线程
                return parentValue;
            }
        };
    
    /**
     * 设置当前线程为演习模式
     * 
     * @param dryRun 是否开启演习模式
     */
    public static void setDryRun(boolean dryRun) {
        DRY_RUN_HOLDER.set(dryRun);
    }
    
    /**
     * 判断当前线程是否处于演习模式
     * 
     * @return true=演习模式，false=正常模式
     */
    public static boolean isDryRun() {
        Boolean value = DRY_RUN_HOLDER.get();
        return value != null && value;
    }
    
    /**
     * 清理当前线程的演习标识
     * <p>
     * 必须在请求结束或任务完成后调用，防止线程池复用导致的污染
     * </p>
     */
    public static void clear() {
        DRY_RUN_HOLDER.remove();
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
     * 
     * @param snapshot 要恢复的演习状态
     */
    public static void restore(boolean snapshot) {
        setDryRun(snapshot);
    }
}

