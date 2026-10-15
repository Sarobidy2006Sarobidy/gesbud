-- =====================================================================
-- Migration : fréquence « Chaque année » (anniversaires, primes, cotisations).
-- À exécuter UNE SEULE FOIS sur une base créée avec l'ancien schema.sql.
-- (L'application fait aussi cette migration automatiquement au démarrage.)
-- =====================================================================

USE gestionnaire_budget;

ALTER TABLE incomes
    MODIFY COLUMN frequence ENUM('PONCTUEL', 'QUOTIDIEN', 'HEBDOMADAIRE', 'MENSUEL', 'ANNUEL')
    NOT NULL DEFAULT 'PONCTUEL';

ALTER TABLE expenses
    MODIFY COLUMN frequence ENUM('PONCTUEL', 'QUOTIDIEN', 'HEBDOMADAIRE', 'MENSUEL', 'ANNUEL')
    NOT NULL DEFAULT 'PONCTUEL';

ALTER TABLE savings
    MODIFY COLUMN frequence ENUM('PONCTUEL', 'QUOTIDIEN', 'HEBDOMADAIRE', 'MENSUEL', 'ANNUEL')
    NOT NULL DEFAULT 'PONCTUEL';

-- Élargir un ENUM ne change aucune valeur existante : les récurrents déjà en base
-- restent tels quels, la nouvelle valeur devient simplement écrite par l'application.
