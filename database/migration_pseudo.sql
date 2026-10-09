-- =====================================================================
-- Migration : pseudo (connexion par pseudo OU email)
-- À exécuter UNE SEULE FOIS sur une base créée avec un ancien schema.sql.
-- (L'application fait aussi cette migration automatiquement au démarrage.)
-- =====================================================================

USE gestionnaire_budget;

ALTER TABLE users
    ADD COLUMN pseudo VARCHAR(30) NULL AFTER email,
    ADD UNIQUE INDEX uq_users_pseudo (pseudo);

-- Les comptes existants n'ont pas de pseudo : ils se connectent avec leur email
-- et peuvent choisir un pseudo dans Paramètres.
