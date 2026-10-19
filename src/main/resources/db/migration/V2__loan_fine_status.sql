-- =====================================================================
-- V2：罚款闭环（第二版）
-- loans 增加 fine_status：NONE（借出中/无罚款）→ UNPAID（归还时有罚款）→ PAID（已缴纳）
-- 存量回填：已归还且罚款 > 0 的记录视为待缴
-- =====================================================================

ALTER TABLE loans
    ADD COLUMN fine_status VARCHAR(16) NOT NULL DEFAULT 'NONE'
        COMMENT '罚款状态：NONE 无罚款 / UNPAID 待缴 / PAID 已缴' AFTER fine_cents;

UPDATE loans SET fine_status = 'UNPAID'
WHERE returned_at IS NOT NULL
  AND fine_cents > 0;
