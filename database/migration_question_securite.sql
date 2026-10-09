-- =====================================================================
-- Migration : question de sécurité (mot de passe oublié)
-- À exécuter UNE SEULE FOIS sur une base créée avec l'ancien schema.sql.
-- (L'application tente aussi cette migration automatiquement au démarrage :
--  ce script sert si vous préférez la faire à la main dans phpMyAdmin.)
-- =====================================================================

USE gestionnaire_budget;

ALTER TABLE users
    ADD COLUMN question_securite VARCHAR(150) NULL AFTER devise,
    ADD COLUMN reponse_securite  VARCHAR(255) NULL AFTER question_securite;

-- Les comptes existants n'ont pas encore de question : ils peuvent s'en
-- définir une dans Paramètres > Question de sécurité (après connexion).
