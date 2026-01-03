package io.github.rollbackme;

import io.github.rollbackme.config.RollbackMeProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RollbackMeProperties 配置属性测试
 * 
 * @author tianhaocui
 */
public class RollbackMePropertiesTest {
    
    @Test
    public void testDefaultValues() {
        RollbackMeProperties properties = new RollbackMeProperties();
        
        // 验证默认值
        assertTrue(properties.isEnabled(), "默认应该启用");
        assertEquals("dry-run", properties.getHeaderName());
        assertFalse(properties.isVerboseLogging(), "默认不开启详细日志");
    }
    
    @Test
    public void testSettersAndGetters() {
        RollbackMeProperties properties = new RollbackMeProperties();
        
        // 测试 enabled
        properties.setEnabled(false);
        assertFalse(properties.isEnabled());
        
        // 测试 headerName
        properties.setHeaderName("X-Dry-Run");
        assertEquals("X-Dry-Run", properties.getHeaderName());
        
        // 测试 verboseLogging
        properties.setVerboseLogging(true);
        assertTrue(properties.isVerboseLogging());
    }
}

