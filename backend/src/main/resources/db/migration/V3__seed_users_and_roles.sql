INSERT INTO users (user_uuid, email, password_hash, full_name, enabled) VALUES
    ('a1e8c4d2-7b3f-4e6a-9c15-2d8f0b6e3a91',
     'processor@medpay.test',
     '$2y$12$mgC4brXa3M3pnmQ1NCjY3O4p6.5fMhzdrb/tmGPeWsGeXbeErzURy',
     'Priya Raman',
     TRUE),

    ('b2f9d5e3-8c4a-4f7b-8d26-3e9a1c7f4b02',
     'reviewer@medpay.test',
     '$2y$12$LpXxE44RaaatCt1Y.peMUuT7wsMMK4/tjhmu6RM8ssjHeSKEc6udq',
     'Dr. Marcus Oyelaran',
     TRUE),

    ('c3a0e6f4-9d5b-4a8c-9e37-4f0b2d8a5c13',
     'auditor@medpay.test',
     '$2y$12$1VI9sXEx1Dtb5Dsgy9Vkl.DGBBiUmANcDwVEleSM8y2WHRx/9CzEO',
     'Helena Vasquez',
     TRUE);

INSERT INTO user_roles (user_id, role)
SELECT id, 'CLAIMS_PROCESSOR' FROM users WHERE email = 'processor@medpay.test';

INSERT INTO user_roles (user_id, role)
SELECT id, 'MEDICAL_REVIEWER' FROM users WHERE email = 'reviewer@medpay.test';

INSERT INTO user_roles (user_id, role)
SELECT id, 'AUDITOR' FROM users WHERE email = 'auditor@medpay.test';
