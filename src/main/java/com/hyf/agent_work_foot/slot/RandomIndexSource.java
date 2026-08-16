package com.hyf.agent_work_foot.slot;

/** 随机索引抽象，使生产随机与测试固定序列使用同一选择流程。 */
public interface RandomIndexSource {
    /** 作用：生成候选索引。输入：大于0的上界。输出：0至bound-1。逻辑：实现必须保证均匀且不越界。 */
    int nextIndex(int bound);
}
