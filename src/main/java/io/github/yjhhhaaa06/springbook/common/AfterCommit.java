package io.github.yjhhhaaa06.springbook.common;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 把「派生数据同步」推迟到事务提交之后执行。
 *
 * <p><b>为什么必须等到提交后</b>：内存索引是 MySQL 的只读视图，只能反映已提交的事实。
 * 在事务内同步，一旦回滚就会留下库里不存在的条目；在提交前同步，并发读会看到尚未生效的数据。
 * 因此 {@code BookIndex}、{@code BookMemoryIndex} 的所有 {@code syncXxx} 都走这里。
 *
 * <p><b>为什么没有事务时立即执行</b>：这种调用（单元测试、启动装载、非事务查询）不存在
 * 「回滚」语义，推迟反而会让索引与数据脱节 —— 没有人会再来触发它。
 */
public final class AfterCommit {

    private AfterCommit() {
    }

    public static void run(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
