-- =====================================================================
-- Migration : revenus ET dépenses récurrents (jour / semaine / mois).
-- À exécuter UNE SEULE FOIS sur une base créée avec l'ancien schema.sql.
-- (L'application fait aussi cette migration automatiquement au démarrage.)
-- Si une colonne existe déjà, MySQL le signale : ignorer cette ligne.
-- =====================================================================

USE gestionnaire_budget;

-- Revenus
ALTER TABLE incomes
    ADD COLUMN frequence           ENUM('PONCTUEL','QUOTIDIEN','HEBDOMADAIRE','MENSUEL') NOT NULL DEFAULT 'PONCTUEL' AFTER date_revenu,
    ADD COLUMN date_fin            DATE NULL AFTER frequence,
    ADD COLUMN derniere_generation DATE NULL AFTER date_fin,
    ADD COLUMN id_source           INT  NULL AFTER derniere_generation;
ALTER TABLE incomes
    ADD CONSTRAINT fk_revenu_source FOREIGN KEY (id_source) REFERENCES incomes(id_revenu) ON DELETE CASCADE;

-- Dépenses
ALTER TABLE expenses
    ADD COLUMN frequence           ENUM('PONCTUEL','QUOTIDIEN','HEBDOMADAIRE','MENSUEL') NOT NULL DEFAULT 'PONCTUEL' AFTER date_depense,
    ADD COLUMN date_fin            DATE NULL AFTER frequence,
    ADD COLUMN derniere_generation DATE NULL AFTER date_fin,
    ADD COLUMN id_source           INT  NULL AFTER derniere_generation;
ALTER TABLE expenses
    ADD CONSTRAINT fk_depense_source FOREIGN KEY (id_source) REFERENCES expenses(id_depense) ON DELETE CASCADE;

-- Les revenus et dépenses existants restent PONCTUEL et fixes : rien ne change pour eux.
