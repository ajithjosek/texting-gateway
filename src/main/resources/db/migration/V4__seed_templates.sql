-- ETG V4: seed servicing/billing templates (EN). ES versions land with S3 localization.
INSERT INTO templates (template_key, locale, version, body, active) VALUES
('payment_due', 'en', 1, 'Hi {{first_name}}, your premium of {{amount}} for policy {{policy_number}} is due {{due_date}}. Pay: {{pay_link}} Reply STOP to opt out.', TRUE),
('payment_failed', 'en', 1, 'Hi {{first_name}}, your payment of {{amount}} for policy {{policy_number}} failed. Update payment: {{pay_link}} Reply STOP to opt out.', TRUE),
('payment_receipt', 'en', 1, 'Thanks {{first_name}}! We received {{amount}} for policy {{policy_number}}. Receipt: {{receipt_link}}', TRUE),
('renewal_reminder', 'en', 1, 'Hi {{first_name}}, policy {{policy_number}} renews {{due_date}}. Review and e-sign: {{esign_link}} Reply STOP to opt out.', TRUE);
