package io.github.rollbackme.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RollbackMe 配置属性
 * <p>
 * 通过 application.yml 或 application.properties 配置演习模式的行为
 * </p>
 * 
 * <h3>配置示例 (YAML)</h3>
 * <pre>
 * rollback-me:
 *   enabled: true
 *   header-name: dry-run
 * </pre>
 * 
 * <h3>配置示例 (Properties)</h3>
 * <pre>
 * rollback-me.enabled=true
 * rollback-me.header-name=dry-run
 * </pre>
 * 
 * @author tianhaocui
 */
@ConfigurationProperties(prefix = "rollback-me")
public class RollbackMeProperties {
    
    /**
     * 是否启用演习模式功能
     * <p>
     * 设置为 false 可以完全禁用 RollbackMe 的所有功能。
     * 默认为 true。
     * </p>
     */
    private boolean enabled = true;
    
    /**
     * HTTP 请求头的 Key 名称
     * <p>
     * 用于标识当前请求是否为演习模式。
     * 当 HTTP 请求中包含此 Header 且值为 "true" 时，触发演习模式。
     * </p>
     * 
     * <p>示例：</p>
     * <pre>
     * curl -H "dry-run: true" http://localhost:8080/api/order
     * </pre>
     */
    private String headerName = "dry-run";
    
    /**
     * 是否启用详细日志
     * <p>
     * 开启后会打印演习模式的执行细节，便于调试。
     * 默认为 false。
     * </p>
     */
    private boolean verboseLogging = false;
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public String getHeaderName() {
        return headerName;
    }
    
    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }
    
    public boolean isVerboseLogging() {
        return verboseLogging;
    }
    
    public void setVerboseLogging(boolean verboseLogging) {
        this.verboseLogging = verboseLogging;
    }
}

