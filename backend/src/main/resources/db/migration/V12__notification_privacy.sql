ALTER TABLE notification_mail_outbox ADD COLUMN subject_scope varchar(38);
CREATE INDEX notification_mail_subject_idx ON notification_mail_outbox(subject_scope,state);
-- One-time migration correlation; runtime repositories stay within their module.
UPDATE notification_mail_outbox n SET subject_scope=o.owner_scope FROM sales_order o
    WHERE n.dedup_key LIKE 'ORDER:' || o.id::text || ':%';
GRANT DELETE ON notification_mail_outbox TO "${applicationUser}";
