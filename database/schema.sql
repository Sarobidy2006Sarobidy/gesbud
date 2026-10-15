-- =====================================================================
-- Base de données : Gestionnaire de Budget Personnel
-- SGBD : MySQL (via WampServer)
-- =====================================================================

CREATE DATABASE IF NOT EXISTS gestionnaire_budget
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE gestionnaire_budget;

-- ---------------------------------------------------------------------
-- Table : users
-- Contient les comptes utilisateurs, leurs préférences (devise)
-- et la question de sécurité servant à récupérer le mot de passe
-- ---------------------------------------------------------------------
CREATE TABLE users (
    id_user         INT AUTO_INCREMENT PRIMARY KEY,
    nom             VARCHAR(50)  NOT NULL,
    prenom          VARCHAR(50)  NOT NULL,
    email           VARCHAR(100) NOT NULL UNIQUE,
    pseudo          VARCHAR(30)  NULL UNIQUE,   -- nom d'utilisateur : connexion par pseudo OU email (sans « @ »)
    mot_de_passe    VARCHAR(255) NOT NULL,      -- hash PBKDF2 (pbkdf2$itérations$sel$hash)
    devise          ENUM('Ar', 'EUR', 'USD', 'GBP') NOT NULL DEFAULT 'Ar',
    question_securite VARCHAR(150) NULL,        -- question choisie (récupération du mot de passe)
    reponse_securite  VARCHAR(255) NULL,        -- hash PBKDF2 de la réponse (jamais en clair)
    date_creation   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- Table : categories
-- Catégories de revenus ou de dépenses, propres à chaque utilisateur
-- ---------------------------------------------------------------------
CREATE TABLE categories (
    id_categorie    INT AUTO_INCREMENT PRIMARY KEY,
    id_user         INT NOT NULL,
    nom             VARCHAR(50) NOT NULL,
    type            ENUM('REVENU', 'DEPENSE') NOT NULL,
    budget          DECIMAL(15,2) NULL CHECK (budget >= 0),
    date_creation   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_categorie_user
        FOREIGN KEY (id_user) REFERENCES users(id_user)
        ON DELETE CASCADE,

    CONSTRAINT uq_categorie_user_nom_type UNIQUE (id_user, nom, type)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- Table : incomes (revenus)
-- ---------------------------------------------------------------------
CREATE TABLE incomes (
    id_revenu       INT AUTO_INCREMENT PRIMARY KEY,
    id_user         INT NOT NULL,
    id_categorie    INT NOT NULL,
    montant         DECIMAL(15,2) NOT NULL CHECK (montant >= 0),
    description     VARCHAR(255),
    date_revenu     DATE NOT NULL,
    -- Revenu récurrent : PONCTUEL (une fois) ou chaque jour / semaine / mois.
    -- L'application crée elle-même les occurrences arrivées à échéance
    -- (lignes avec id_source = revenu d'origine, supprimées avec lui).
    frequence           ENUM('PONCTUEL', 'QUOTIDIEN', 'HEBDOMADAIRE', 'MENSUEL', 'ANNUEL') NOT NULL DEFAULT 'PONCTUEL',
    date_fin            DATE NULL,                  -- fin de la récurrence (facultative)
    derniere_generation DATE NULL,                  -- dernière occurrence déjà créée
    id_source           INT  NULL,                  -- revenu d'origine d'une occurrence automatique
    date_creation   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_revenu_user
        FOREIGN KEY (id_user) REFERENCES users(id_user)
        ON DELETE CASCADE,

    CONSTRAINT fk_revenu_categorie
        FOREIGN KEY (id_categorie) REFERENCES categories(id_categorie)
        ON DELETE RESTRICT,

    CONSTRAINT fk_revenu_source
        FOREIGN KEY (id_source) REFERENCES incomes(id_revenu)
        ON DELETE CASCADE
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- Table : expenses (dépenses)
-- ---------------------------------------------------------------------
CREATE TABLE expenses (
    id_depense      INT AUTO_INCREMENT PRIMARY KEY,
    id_user         INT NOT NULL,
    id_categorie    INT NOT NULL,
    montant         DECIMAL(15,2) NOT NULL CHECK (montant >= 0),
    description     VARCHAR(255),
    date_depense    DATE NOT NULL,
    -- Dépense récurrente (chaque jour / semaine / mois), fixe ou variable (voir incomes).
    frequence           ENUM('PONCTUEL', 'QUOTIDIEN', 'HEBDOMADAIRE', 'MENSUEL', 'ANNUEL') NOT NULL DEFAULT 'PONCTUEL',
    date_fin            DATE NULL,
    derniere_generation DATE NULL,
    id_source           INT  NULL,                  -- dépense d'origine d'une occurrence automatique
    date_creation   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_depense_user
        FOREIGN KEY (id_user) REFERENCES users(id_user)
        ON DELETE CASCADE,

    CONSTRAINT fk_depense_categorie
        FOREIGN KEY (id_categorie) REFERENCES categories(id_categorie)
        ON DELETE RESTRICT,

    CONSTRAINT fk_depense_source
        FOREIGN KEY (id_source) REFERENCES expenses(id_depense)
        ON DELETE CASCADE
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- Table : savings (économies)
-- Argent mis de côté : une seule fois, ou chaque jour / semaine / mois.
-- L'application crée elle-même les occurrences arrivées à échéance
-- (lignes avec id_source = économie d'origine, supprimées avec elle).
-- ---------------------------------------------------------------------
CREATE TABLE savings (
    id_economie         INT AUTO_INCREMENT PRIMARY KEY,
    id_user             INT NOT NULL,
    montant             DECIMAL(15,2) NOT NULL CHECK (montant >= 0),
    description         VARCHAR(255),
    date_economie       DATE NOT NULL,
    frequence           ENUM('PONCTUEL', 'QUOTIDIEN', 'HEBDOMADAIRE', 'MENSUEL', 'ANNUEL') NOT NULL DEFAULT 'PONCTUEL',
    date_fin            DATE NULL,
    derniere_generation DATE NULL,
    id_source           INT  NULL,
    date_creation       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_economie_user
        FOREIGN KEY (id_user) REFERENCES users(id_user)
        ON DELETE CASCADE,

    CONSTRAINT fk_economie_source
        FOREIGN KEY (id_source) REFERENCES savings(id_economie)
        ON DELETE CASCADE
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- Index utiles pour les recherches / filtres / tri
-- ---------------------------------------------------------------------
CREATE INDEX idx_incomes_date       ON incomes(date_revenu);
CREATE INDEX idx_incomes_user       ON incomes(id_user);
CREATE INDEX idx_expenses_date      ON expenses(date_depense);
CREATE INDEX idx_expenses_user      ON expenses(id_user);
CREATE INDEX idx_savings_date       ON savings(date_economie);
CREATE INDEX idx_savings_user       ON savings(id_user);
CREATE INDEX idx_categories_user    ON categories(id_user);

-- ---------------------------------------------------------------------
-- Données de démonstration (optionnel)
-- ---------------------------------------------------------------------
-- INSERT INTO users (nom, prenom, email, mot_de_passe, devise)
-- VALUES ('Pirlo', 'Andrea', 'andrea@example.com', '$2a$10$hashDemo', 'EUR');

-- =====================================================================
-- Diagramme relationnel (résumé texte)
-- =====================================================================
-- users (1) ────< categories (N)
-- users (1) ────< incomes (N)
-- users (1) ────< expenses (N)
-- categories (1) ────< incomes (N)
-- categories (1) ────< expenses (N)
--
-- Un utilisateur possède plusieurs catégories, plusieurs revenus et
-- plusieurs dépenses. Chaque revenu/dépense appartient à une seule
-- catégorie, et chaque catégorie est soit de type REVENU, soit DEPENSE.
