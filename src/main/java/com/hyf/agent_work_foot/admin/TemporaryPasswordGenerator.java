package com.hyf.agent_work_foot.admin;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/** 使用密码学安全随机源生成临时密码；不负责哈希、保存或日志。 */
@Component
public class TemporaryPasswordGenerator {
    private static final char[] ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_".toCharArray();
    private static final int LENGTH = 12;
    private final SecureRandom random = new SecureRandom();

    /** 作用：生成符合项目密码格式的临时密码。输入：无。输出：12位随机字符串。逻辑：每位独立从固定字符集安全抽取。 */
    public String generate() {
        char[] value = new char[LENGTH];
        for (int index = 0; index < value.length; index++) {
            value[index] = ALPHABET[random.nextInt(ALPHABET.length)];
        }
        return new String(value);
    }
}
