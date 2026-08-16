package com.hyf.agent_work_foot.slot;

import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Component;

/** 生产环境JDK均匀随机索引实现，不保存用户或候选业务数据。 */
@Component
public class JdkRandomIndexSource implements RandomIndexSource {
    /** 作用：生成均匀随机索引。输入：候选数量。输出：合法索引。逻辑：使用ThreadLocalRandom避免共享锁。 */
    @Override
    public int nextIndex(int bound) { return ThreadLocalRandom.current().nextInt(bound); }
}
