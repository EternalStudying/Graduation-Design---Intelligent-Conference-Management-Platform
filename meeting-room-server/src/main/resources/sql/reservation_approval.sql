ALTER TABLE reservation
  ADD COLUMN approval_remark VARCHAR(255) NULL AFTER cancel_reason,
  ADD COLUMN reject_reason VARCHAR(255) NULL AFTER approval_remark,
  ADD COLUMN exception_reason VARCHAR(255) NULL AFTER reject_reason,
  ADD COLUMN processed_by BIGINT NULL AFTER exception_reason,
  ADD COLUMN processed_at DATETIME NULL AFTER processed_by,
  MODIFY COLUMN status TINYINT NOT NULL DEFAULT 1 COMMENT '1待审核，2已通过，3已结束，4已取消，5已驳回，6异常';
