INSERT INTO email_sender_pool (email_account, auth_code, status)
VALUES
    ('powerguard@qq.com', 'evsipfdkkrehfadh', 1),
    ('2717412647@qq.com', 'tjfsccydvuzldggh', 1)
ON DUPLICATE KEY UPDATE
    auth_code = VALUES(auth_code),
    status = VALUES(status);
