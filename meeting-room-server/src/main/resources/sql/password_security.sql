-- Run once before enabling hash-only password verification.
-- Default password 123456 -> sha256:8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92
UPDATE sys_user
SET password_hash = 'sha256:8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92'
WHERE password_hash = '123456';
