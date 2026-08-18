INSERT INTO fee_schedules (service_code, description, contracted_rate, effective_from, effective_to) VALUES
    ('MP101', 'Office visit, new patient, straightforward',        125.0000, DATE '2020-01-01', NULL),
    ('MP102', 'Office visit, established patient, low complexity',  95.0000, DATE '2020-01-01', NULL),
    ('MP103', 'Office visit, established patient, moderate',       165.0000, DATE '2020-01-01', NULL),
    ('MP104', 'Preventive visit, adult',                           210.0000, DATE '2020-01-01', NULL),

    ('DX201', 'Basic metabolic panel',                              48.5000, DATE '2020-01-01', NULL),
    ('DX202', 'Complete blood count with differential',             36.7500, DATE '2020-01-01', NULL),
    ('DX203', 'Diagnostic radiograph, two views',                  142.0000, DATE '2020-01-01', NULL),
    ('DX204', 'Magnetic resonance imaging, without contrast',      1450.0000, DATE '2020-01-01', NULL),

    ('SX301', 'Diagnostic arthroscopy, knee',                      4200.0000, DATE '2020-01-01', NULL),
    ('SX302', 'Laparoscopic cholecystectomy',                      8750.0000, DATE '2020-01-01', NULL),
    ('SX303', 'Total knee arthroplasty',                          32000.0000, DATE '2020-01-01', NULL),
    ('SX304', 'Coronary artery bypass, triple vessel',            61500.0000, DATE '2020-01-01', NULL),

    ('FC401', 'Inpatient bed day, medical/surgical',               2400.0000, DATE '2020-01-01', NULL),
    ('FC402', 'Emergency department visit, high severity',         1875.0000, DATE '2020-01-01', NULL);

INSERT INTO fee_schedules (service_code, description, contracted_rate, effective_from, effective_to) VALUES
    ('RT501', 'Physical therapy, therapeutic exercise (2020-2022 rate)', 780.0000, DATE '2020-01-01', DATE '2022-12-31'),
    ('RT501', 'Physical therapy, therapeutic exercise (2023- rate)',     845.0000, DATE '2023-01-01', NULL);
