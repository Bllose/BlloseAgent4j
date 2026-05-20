package com.bllose.agent.guard;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import java.util.ArrayList;
import java.util.List;

/**
 * 映射 guard-rules.yml 中的规则配置。
 * 修改 guard-rules.yml 后重启服务即可生效，无需重新编译。
 */
@Configuration
@ConfigurationProperties(prefix = "guard")
@PropertySource(value = "classpath:guard-rules.yml", factory = YamlPropertySourceFactory.class)
public class GuardRulesConfig {

    private int maxInputLength = 2000;
    private List<String> injectionPatterns = new ArrayList<>();
    private List<String> technicalAttackPatterns = new ArrayList<>();
    private Guest guest = new Guest();

    public int getMaxInputLength() { return maxInputLength; }
    public void setMaxInputLength(int maxInputLength) { this.maxInputLength = maxInputLength; }

    public List<String> getInjectionPatterns() { return injectionPatterns; }
    public void setInjectionPatterns(List<String> injectionPatterns) { this.injectionPatterns = injectionPatterns; }

    public List<String> getTechnicalAttackPatterns() { return technicalAttackPatterns; }
    public void setTechnicalAttackPatterns(List<String> technicalAttackPatterns) { this.technicalAttackPatterns = technicalAttackPatterns; }

    public Guest getGuest() { return guest; }
    public void setGuest(Guest guest) { this.guest = guest; }

    public static class Guest {
        private List<String> paperWhitelistEn = new ArrayList<>();
        private List<String> paperWhitelistCn = new ArrayList<>();

        public List<String> getPaperWhitelistEn() { return paperWhitelistEn; }
        public void setPaperWhitelistEn(List<String> paperWhitelistEn) { this.paperWhitelistEn = paperWhitelistEn; }

        public List<String> getPaperWhitelistCn() { return paperWhitelistCn; }
        public void setPaperWhitelistCn(List<String> paperWhitelistCn) { this.paperWhitelistCn = paperWhitelistCn; }
    }
}
