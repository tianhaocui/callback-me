package io.github.rollbackme.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 演习模式注解
 * <p>
 * 标注在方法上，表示该方法支持演习模式（Dry Run）。
 * 当检测到演习标识时（通过 HTTP Header 或线程上下文），会开启新事务并在执行完毕后强制回滚。
 * </p>
 * 
 * <h3>使用场景</h3>
 * <ul>
 *   <li>压测演练：生产环境执行压测，但不产生脏数据</li>
 *   <li>功能验证：验证业务逻辑是否正确，但不实际落库</li>
 *   <li>安全测试：测试系统在特定条件下的行为，但不修改数据</li>
 * </ul>
 * 
 * <h3>多线程支持</h3>
 * <p>
 * 当 {@link #propagateToChildThread()} 为 true 时（默认），演习标识会自动传递到子线程。
 * 需要配合 {@link io.github.rollbackme.support.DryRunTaskDecorator} 使用，
 * 确保在异步任务执行时正确传递上下文。
 * </p>
 * 
 * @author tianhaocui
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DryRun {
    
    /**
     * 指定事务管理器的 Bean 名称
     * <p>
     * 如果系统中有多个事务管理器，可以通过此属性指定使用哪一个。
     * 留空则使用默认的事务管理器。
     * </p>
     * 
     * @return 事务管理器 Bean 名称，默认为空字符串
     */
    String transactionManager() default "";
    
    /**
     * 是否将演习标识传播到子线程
     * <p>
     * 当方法内部创建异步任务或新线程时，此标识决定是否将演习模式传递给子线程。
     * 默认为 true，建议保持开启以避免子线程产生脏数据。
     * </p>
     * 
     * <p>
     * <b>注意：</b>需要配合 DryRunTaskDecorator 使用才能在线程池场景下生效。
     * </p>
     * 
     * @return true=传播到子线程，false=不传播
     */
    boolean propagateToChildThread() default true;
}

