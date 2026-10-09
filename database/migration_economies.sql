-- =====================================================================
-- Migration : économies (ponctuelles ou chaque jour / semaine / mois)
-- À exécuter UNE SEULE FOIS sur une base créée avec un ancien schema.sql.
-- (L'application crée aussi cette table automatiquement au démarrage.)
-- =====================================================================

USE gestionnaire_budget;

CREATE TABLE IF NOT EXISTS savings (
    id_economie         INT AUTO_INCREMENT PRIMARY KEY,
    id_user             INT NOT NULL,
    montant             DECIMAL(15,2) NOT NULL CHECK (montant >= 0),
    description         VARCHAR(255),
    date_economie       DATE NOT NULL,
    frequence           ENUM('PONCTUEL', 'QUOTIDIEN', 'HEBDOMADAIRE', 'MENSUEL') NOT NULL DEFAULT 'PONCTUEL',
    date_fin            DATE NULL,
    derniere_generation DATE NULL,
    id_source           INT  NULL,
    date_creation       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_economie_user   FOREIGN KEY (id_user)   REFERENCES users(id_user) ON DELETE CASCADE,
    CONSTRAINT fk_economie_source FOREIGN KEY (id_source) REFERENCES savings(id_economie) ON DELETE CASCADE
) ENGINE=InnoDB;
