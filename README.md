# RollbackMe - Spring Boot 事务演习框架

[![Maven Central](https://img.shields.io/maven-central/v/io.github.tianhaocui/rollback-me-spring-boot-starter.svg)](https://search.maven.org/artifact/io.github.tianhaocui/rollback-me-spring-boot-starter)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

一个支持多线程的 Spring Boot Starter，提供 `@DryRun` 注解实现"无损演习"：执行完整业务逻辑后强制回滚事务，不产生任何脏数据。

## 📖 核心特性

- ✅ **零侵入式设计**：仅需一个注解，不改变业务代码
- ✅ **多线程支持**：自动传递演习标识到子线程和异步任务
- ✅ **强制回滚**：无论成功失败，都会回滚事务
- ✅ **灵活触发**：支持 HTTP Header 或程序化触发
- ✅ **线程安全**：基于 `InheritableThreadLocal` + `TaskDecorator` 实现
- ✅ **Spring Boot 原生支持**：自动装配，开箱即用

## 🚀 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>io.github.tianhaocui</groupId>
    <artifactId>rollback-me-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. 在方法上添加注解

```java
@Service
public class OrderService {
    
    @DryRun
    @Transactional
    public void createOrder(OrderDTO order) {
        // 执行真实的业务逻辑
        orderRepository.save(order);
        inventoryService.deductStock(order.getProductId());
        // 如果是演习模式，这里会自动回滚，数据库不会有任何变化
    }
}
```

### 3. 发起演习请求

```bash
# 通过 HTTP Header 触发演习模式
curl -X POST http://localhost:8080/api/order \
  -H "dry-run: true" \
  -H "Content-Type: application/json" \
  -d '{"productId": 123, "quantity": 5}'
```

✨ **结果**：业务逻辑完整执行，但事务被强制回滚，数据库无任何变化！

---

## 🔧 配置说明

### application.yml

```yaml
rollback-me:
  enabled: true                          # 是否启用演习模式（默认 true）
  header-name: dry-run                   # HTTP Header 名称（默认）
  verbose-logging: false                 # 是否开启详细日志（默认 false）
```

### application.properties

```properties
rollback-me.enabled=true
rollback-me.header-name=dry-run
rollback-me.verbose-logging=true
```

---

## 🌟 多线程支持（重要）

RollbackMe 的核心优势之一是**完整支持多线程和异步场景**。要启用此功能，需要配置 `DryRunTaskDecorator`。

### 为什么需要 TaskDecorator？

在异步任务中，Spring 使用线程池执行 `@Async` 方法。默认情况下，`ThreadLocal` 无法自动传递到线程池中的工作线程。`DryRunTaskDecorator` 解决了这个问题：

1. **提交任务时**：捕获主线程的演习标识
2. **执行任务前**：将标识恢复到工作线程
3. **执行任务后**：清理标识，避免污染线程池

### 配置方式一：使用自动注入的 Decorator

```java
@Configuration
@EnableAsync
public class AsyncConfig {
    
    @Bean
    public ThreadPoolTaskExecutor taskExecutor(DryRunTaskDecorator decorator) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("async-");
        
        // 🔑 关键：设置装饰器
        executor.setTaskDecorator(decorator);
        
        executor.initialize();
        return executor;
    }
}
```

### 配置方式二：手动创建 Decorator

```java
@Configuration
@EnableAsync
public class AsyncConfig {
    
    @Bean
    public ThreadPoolTaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        
        // 手动创建装饰器
        executor.setTaskDecorator(new DryRunTaskDecorator());
        
        executor.initialize();
        return executor;
    }
}
```

### 配置方式三：多个线程池统一配置

```java
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {
    
    @Autowired
    private DryRunTaskDecorator dryRunTaskDecorator;
    
    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(20);
        executor.setTaskDecorator(dryRunTaskDecorator);
        executor.initialize();
        return executor;
    }
}
```

### 使用示例

配置完成后，异步方法会自动继承演习标识：

```java
@Service
public class OrderService {
    
    @Autowired
    private NotificationService notificationService;
    
    @DryRun
    @Transactional
    public void createOrder(OrderDTO order) {
        // 主线程：保存订单
        orderRepository.save(order);
        
        // 异步线程：发送通知（自动继承演习标识）
        notificationService.sendEmailAsync(order);
        
        // 两个线程的数据都会被回滚！
    }
}

@Service
public class NotificationService {
    
    @Async
    @Transactional
    public void sendEmailAsync(OrderDTO order) {
        // 这里依然处于演习模式
        // 邮件记录会被保存到数据库，但最终会回滚
        emailLogRepository.save(new EmailLog(order));
    }
}
```

---

## 📚 高级用法

### 1. 指定事务管理器

如果项目中有多个事务管理器，可以通过注解属性指定：

```java
@DryRun(transactionManager = "secondaryTransactionManager")
@Transactional(transactionManager = "secondaryTransactionManager")
public void operateSecondaryDatabase() {
    // 使用指定的事务管理器
}
```

### 2. 程序化触发演习模式

除了 HTTP Header，还可以通过代码直接触发：

```java
@Service
public class TestService {
    
    public void runTest() {
        // 手动开启演习模式
        DryRunContext.setDryRun(true);
        
        try {
            // 这里的所有带 @DryRun 的方法都会回滚
            orderService.createOrder(order);
        } finally {
            // 记得清理
            DryRunContext.clear();
        }
    }
}
```

### 3. 控制子线程传播

如果不想将演习标识传播到子线程，可以设置：

```java
@DryRun(propagateToChildThread = false)
public void parentMethod() {
    // 子线程不会继承演习标识
}
```

---

## 🧪 测试场景

### 1. 压测演练

在生产环境执行压测，但不产生脏数据：

```bash
# 压测工具配置
Header: dry-run=true
```

### 2. 功能验证

验证复杂业务流程是否正确，但不实际落库：

```java
@Test
public void testComplexBusinessFlow() {
    DryRunContext.setDryRun(true);
    try {
        orderService.createOrder(order);
        // 验证流程是否正确执行
        assertTrue(orderService.isProcessed());
    } finally {
        DryRunContext.clear();
    }
}
```

### 3. 安全测试

测试异常场景下的系统行为，但不污染数据：

```bash
curl -X POST http://localhost:8080/api/order \
  -H "dry-run: true" \
  -d '{"malicious": "payload"}'
```

---

## 🏗️ 架构设计

### 核心组件

| 组件 | 职责 | 说明 |
|------|------|------|
| `DryRunContext` | 上下文持有者 | 使用 `InheritableThreadLocal` 存储演习标识 |
| `@DryRun` | 注解 | 标记需要演习的方法 |
| `DryRunAspect` | AOP 切面 | 拦截注解方法，控制事务回滚 |
| `DryRunTaskDecorator` | 任务装饰器 | 在异步场景下传递演习标识 |
| `RollbackMeProperties` | 配置属性 | 全局配置 |
| `RollbackMeAutoConfiguration` | 自动装配 | Spring Boot 自动配置 |

### 执行流程

```
1. HTTP 请求 + Header
   ↓
2. DryRunAspect 拦截 @DryRun 方法
   ↓
3. 检查 Header 或 DryRunContext
   ↓
4. 开启新事务（REQUIRES_NEW）
   ↓
5. 执行业务逻辑
   ├─ 主线程操作数据库
   └─ 异步线程（通过 TaskDecorator 传递标识）
   ↓
6. Finally: 强制回滚事务
   ↓
7. 清理 DryRunContext
```

### 多线程传递原理

```java
// 主线程
DryRunContext.setDryRun(true);  // 标记演习模式

// TaskDecorator 在提交任务时
boolean snapshot = DryRunContext.snapshot();  // 捕获状态

// 工作线程执行前
DryRunContext.restore(snapshot);  // 恢复状态

// 工作线程执行后
DryRunContext.clear();  // 清理状态
```

---

## ⚠️ 注意事项

### 1. 必须配置 TaskDecorator

如果使用 `@Async` 或手动创建线程池，**必须**配置 `DryRunTaskDecorator`，否则子线程无法继承演习标识，可能产生脏数据！

```java
// ❌ 错误：未设置 TaskDecorator
@Bean
public ThreadPoolTaskExecutor taskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.initialize();
    return executor;
}

// ✅ 正确：设置 TaskDecorator
@Bean
public ThreadPoolTaskExecutor taskExecutor(DryRunTaskDecorator decorator) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setTaskDecorator(decorator);  // 关键
    executor.initialize();
    return executor;
}
```

### 2. 事务传播级别

`@DryRun` 内部使用 `PROPAGATION_REQUIRES_NEW` 开启独立事务。如果业务方法本身也有 `@Transactional`，请注意事务传播级别的影响。

### 3. 非事务方法

如果方法内没有数据库操作，`@DryRun` 不会产生任何效果（因为没有事务可回滚）。

---

## 🛠️ 开发者指南

### 本地构建

```bash
# 克隆项目
git clone https://github.com/tianhaocui/callback-me.git
cd callback-me

# 编译并安装到本地
mvn clean install
```

### 运行测试

```bash
mvn test
```

### 发布到 Maven Central

```bash
# 配置 GPG 密钥
gpg --gen-key

# 发布
mvn clean deploy -P release
```

---

## 📄 许可证

本项目基于 [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) 开源。

---

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

1. Fork 本项目
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 提交 Pull Request

---

## 📞 联系我们

- 🐛 Issues: [GitHub Issues](https://github.com/tianhaocui/callback-me/issues)
- 📖 项目地址: [GitHub Repository](https://github.com/tianhaocui/callback-me)

---

## ⭐ Star History

如果这个项目对你有帮助，请给我们一个 Star ⭐️

---

**让压测和演习更安全，让生产环境更放心！**

