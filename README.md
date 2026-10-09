# Gestionnaire de Budget Personnel

Application Desktop **100% Java** (JavaFX) pour la gestion d'un budget
personnel (revenus, dépenses, catégories, statistiques), avec base de
données MySQL. Aucune bibliothèque tierce de thème ou d'icônes : tout
(y compris les icônes) est dessiné avec l'API standard de JavaFX.

## Architecture

Le projet suit une architecture **MVC + DAO** :

```
GestionnaireBudgetPersonnel/
│
├── pom.xml                          # Maven : JavaFX + driver JDBC MySQL uniquement
├── database/
│   ├── schema.sql                   # Script SQL complet (tables, clés, contraintes)
│   └── migration_question_securite.sql  # Ajoute la question de sécurité à une base existante
│
└── src/main/
    ├── java/com/budgetmanager/
    │   ├── Main.java                # Point d'entrée JavaFX + transition en fondu entre écrans
    │   ├── config/
    │   │   └── DatabaseConnection.java   # Connexion JDBC (singleton)
    │   ├── model/                   # Classes représentant les données (POJO)
    │   │   ├── User.java
    │   │   ├── Category.java
    │   │   ├── CategoryType.java
    │   │   ├── Income.java
    │   │   └── Expense.java
    │   ├── dao/                     # Accès aux données (requêtes SQL via JDBC)
    │   │   ├── UserDAO.java
    │   │   ├── CategoryDAO.java
    │   │   ├── IncomeDAO.java
    │   │   └── ExpenseDAO.java
    │   ├── controller/              # Logique de chaque écran JavaFX (FXML)
    │   │   ├── LoginController.java          # Connexion email + mot de passe
    │   │   ├── RegisterController.java       # Inscription (+ question de sécurité)
    │   │   ├── ForgotPasswordController.java # Mot de passe oublié : email -> question -> nouveau mot de passe
    │   │   ├── AuthHeroController.java       # Badges hexagonaux animés du panneau de gauche
    │   │   ├── SidebarController.java   # Barre latérale incluse (fx:include) dans chaque écran
    │   │   ├── DashboardController.java
    │   │   ├── IncomeController.java
    │   │   ├── ExpenseController.java
    │   │   ├── CategoryController.java
    │   │   ├── HistoryController.java
    │   │   ├── StatisticsController.java
    │   │   └── SettingsController.java
    │   └── util/                    # Classes utilitaires transverses
    │       ├── Icons.java           # Icônes vectorielles 100% Java (Shape API de JavaFX)
    │       ├── Hexagon.java         # Badge hexagonal lumineux (logo + décor de connexion)
    │       ├── PasswordUtil.java    # Hachage PBKDF2 des mots de passe et des réponses de sécurité
    │       ├── SecurityQuestions.java  # Liste des questions de sécurité proposées
    │       ├── AuthUi.java          # Focus des champs, bandeaux de message des écrans d'authentification
    │       ├── FlashMessage.java    # Message affiché une fois sur l'écran suivant
    │       ├── SessionManager.java  # Utilisateur connecté (session)
    │       ├── CurrencyUtil.java    # Formatage selon la devise
    │       └── AlertUtil.java       # Boîtes de dialogue JavaFX
    │
    └── resources/com/budgetmanager/
        ├── view/                    # Fichiers FXML (un par écran + la sidebar partagée)
        │   ├── login.fxml
        │   ├── register.fxml
        │   ├── forgot-password.fxml  # Mot de passe oublié (3 étapes)
        │   ├── auth-hero.fxml        # Panneau décoratif commun aux 3 écrans d'authentification
        │   ├── sidebar.fxml          # Barre latérale "pilule", incluse via <fx:include>
        │   ├── dashboard.fxml
        │   ├── income.fxml
        │   ├── expense.fxml
        │   ├── category.fxml
        │   ├── history.fxml
        │   ├── statistics.fxml
        │   └── settings.fxml
        ├── css/
        │   └── style.css            # Thème "doux" (dégradés discrets, grands arrondis, ombres légères)
        └── fonts/                   # Polices embarquées, chargées par Main.java au démarrage
            ├── Poppins-Regular.ttf  # Titres, montants, marque (Poppins)
            ├── Poppins-Medium.ttf
            ├── Poppins-Bold.ttf
            ├── Inter-Regular.otf    # Texte courant, champs, tableaux (Inter)
            ├── Inter-Medium.otf
            └── Inter-SemiBold.otf
```

### Rôle de chaque couche
- **model** : représente les données telles qu'elles existent en base (aucune logique SQL).
- **dao** : seule couche qui exécute des requêtes SQL (JDBC), isole totalement l'accès aux données.
- **controller** : relie les vues FXML aux DAO ; contient la logique de l'écran (validations).
  `SidebarController` centralise la navigation entre écrans pour éviter de la dupliquer 9 fois.
- **view (FXML)** : décrit l'interface graphique, sans logique métier.
- **util** : fonctions transverses (icônes, session utilisateur, formatage devise, alertes).

## Mise en route

1. **Démarrer WampServer** et s'assurer que MySQL tourne sur `localhost:3306`.
2. Exécuter le script `database/schema.sql` dans phpMyAdmin (ou en ligne de commande MySQL)
   pour créer la base `gestionnaire_budget` et ses tables.
3. Vérifier les identifiants de connexion dans
   `src/main/java/com/budgetmanager/config/DatabaseConnection.java`
   (par défaut : utilisateur `root`, mot de passe vide — configuration WampServer standard).
4. Ouvrir le dossier `GestionnaireBudgetPersonnel` dans **IntelliJ IDEA** en tant que projet Maven
   (IntelliJ détecte automatiquement le `pom.xml` et télécharge JavaFX + le driver MySQL —
   ce sont les deux seules dépendances du projet).
5. Lancer la classe `Main.java`, ou exécuter :
   ```
   mvn clean javafx:run
   ```

## Fonctionnalités incluses

- Authentification : inscription, connexion par **email + mot de passe**, déconnexion
- **Mot de passe oublié par question de sécurité** : email -> réponse à la question choisie à
  l'inscription (3 essais maximum) -> nouveau mot de passe. La question se définit ou se change
  aussi dans Paramètres (mot de passe actuel exigé)
- Gestion des revenus et des dépenses (ajout, modification, suppression, recherche, filtres)
- **Configuration de la base** : par défaut `root` sans mot de passe (WampServer) ; pour changer, utiliser `database.properties` (exemple fourni à la racine) ou les variables `BUDGET_DB_URL`, `BUDGET_DB_USER`, `BUDGET_DB_PASSWORD`
- **Sécurité** : mot de passe de 8 caractères minimum, PBKDF2 310 000 itérations (les anciens hachages sont re-hachés à la connexion, les mots de passe en clair d'avant sont hachés au démarrage), 5 échecs de connexion = attente de 60 secondes
- **Règle des totaux** : seules les opérations datées d'aujourd'hui ou avant comptent dans les totaux, le solde et les statistiques ; une opération datée dans le futur est « prévue » (visible dans les listes, pas encore comptée)
- **Changer un montant récurrent** (loyer, salaire, épargne) : le nouveau montant s'applique à partir de la prochaine échéance, ce qui est déjà enregistré ne change pas
- **Responsive** : fenêtre redimensionnable (minimum 920×640) qui garde sa taille d'une page à l'autre ; toutes les pages s'adaptent (cartes du tableau de bord sur 2 à 5 colonnes, graphiques sur 1 ou 2 colonnes, formulaires et filtres sur plusieurs lignes, pages défilantes, colonnes de tableaux ajustées) ; les montants des tableaux sont affichés avec la devise
- **Connexion par pseudo ou email** avec mot de passe. Le pseudo est choisi à l'inscription (3 à 30 caractères : lettres, chiffres, `.`, `-`, `_`) ; les anciens comptes gardent leur email et peuvent ajouter un pseudo dans Paramètres. Migration automatique de la colonne `pseudo` au démarrage (ou `database/migration_pseudo.sql`)
- **Logique du budget** : solde disponible = revenus − dépenses − économies (tableau de bord, statistiques) ; taux d'épargne = économies ÷ revenus ; une alerte demande confirmation si une dépense ou une économie dépasse le solde disponible ; l'historique affiche aussi les économies
- **Économies** (menu « Économies ») : mettre de l'argent de côté une seule fois ou chaque jour / semaine / mois, voir le total économisé et la courbe du cumul par jour, par semaine ou par mois (table `savings`, créée automatiquement au démarrage ou via `database/migration_economies.sql`)
- **Revenus et dépenses récurrents** : une seule fois, chaque jour, chaque semaine ou chaque mois (date de fin facultative). Aucun montant n'est figé : salaire, loyer, urgence… tout peut être corrigé ligne par ligne. L'application crée automatiquement les occurrences arrivées à échéance ; la base est migrée seule au démarrage (ou via `database/migration_revenus_recurrents.sql`)
- Gestion des catégories (types Revenu / Dépense)
- Tableau de bord (solde, totaux, nombre d'opérations, 5 cartes statistiques pastel)
- Statistiques (PieChart, BarChart mensuel, LineChart d'évolution du solde)
- Historique unifié avec recherche, tri et filtres
- Paramètres utilisateur (profil, mot de passe, devise : Ariary, Euro, Dollar, Livre Sterling)
- Thème visuel bleu bancaire (inspiré de la page de connexion « S2Biz ») sur tous les écrans, boîtes de dialogue et graphiques

## Design : 100% Java, sans aucune bibliothèque tierce

- **Icônes** : `util/Icons.java` dessine chaque icône (tableau de bord, revenus, dépenses,
  catégories, historique, statistiques, paramètres, déconnexion, ajouter, modifier, supprimer,
  filtrer, etc.) directement avec l'API `Shape` de JavaFX (`Circle`, `Line`, `Rectangle`,
  `Polygon`, `Polyline`, `Arc`). Aucune image, aucun emoji, aucune bibliothèque d'icônes externe.
- **Polices** : 2 familles embarquées dans `resources/fonts/`, chargées par `Main.java` au
  lancement (indépendantes des polices installées sur le poste) :
  - **Poppins** (Regular/Medium/Bold) pour les titres, montants et éléments de marque.
  - **Inter** (Regular/Medium/SemiBold) pour le texte courant, les champs et les tableaux.
  - Les deux sont sous licence SIL Open Font License (libres de redistribution).
- **Thème bleu** : `style.css` — fond bleu profond et panneau translucide pour la connexion,
  champs blancs avec icône, bouton bleu ciel ; cartes blanches, tableaux, graphiques et
  boîtes de dialogue (`AlertUtil`) dans la même palette. Les icônes d'état (succès, erreur,
  question...) sont dessinées avec `Icons`, jamais d'emoji.
- **Polices** : *Poppins* pour les titres / la marque, *Inter* pour le texte. Les graisses
  intermédiaires sont des fichiers distincts : en CSS on utilise donc les familles
  `"Inter SemiBold"`, `"Inter Medium"` et `"Poppins Medium"` (et non `-fx-font-weight`).
- **Sidebar "pilule"** : barre de navigation bleu nuit, arrondie, avec icônes seules et
  infobulles (`Tooltip`) pour le nom de chaque section — `sidebar.fxml` + `SidebarController`,
  réutilisée par les 7 écrans principaux via `<fx:include>` (un seul endroit à maintenir).
- **Transitions douces** : `Main.changerScene()` applique un fondu (`FadeTransition`, 220 ms)
  à chaque nouvel écran plutôt que de l'afficher d'un coup — la navigation ne saute jamais
  brutalement d'une page à l'autre.
- **Cartes teintées** : chaque carte du tableau de bord a une teinte et un filet d'accent
  (vert = revenus, rouge = dépenses, bleu = solde, cyan / indigo = compteurs).

## Points à renforcer avant une mise en production

- **Sécurité** : mots de passe et réponses de sécurité sont hachés en PBKDF2-HMAC-SHA256 avec
  sel (`PasswordUtil`, JDK uniquement). Les comptes créés avant cette version, dont le mot de
  passe est encore en clair, sont convertis automatiquement à leur prochaine connexion. Ces
  comptes n'ont pas de question de sécurité : ils doivent en définir une dans Paramètres.
- Base existante : l'application ajoute seule les colonnes `question_securite` /
  `reponse_securite` au démarrage ; sinon exécuter `database/migration_question_securite.sql`.
- Ajouter une validation plus poussée des champs (formats email, montants négatifs, etc.).
- Ajouter des tests unitaires sur la couche DAO (avec une base de test dédiée).
- Vérifications faites : compilation avec JDK 21 + OpenJFX, chargement de tous les écrans FXML,
  rendu des écrans (connexion, inscription, mot de passe oublié, pages internes, dialogues) et
  tests du hachage. **Non testé** : les requêtes contre une vraie base MySQL (pas de serveur
  disponible) — à essayer avec WampServer, surtout inscription -> mot de passe oublié.
