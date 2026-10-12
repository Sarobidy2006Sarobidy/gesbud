package com.budgetmanager.controller;

import com.budgetmanager.Main;
import com.budgetmanager.dao.UserDAO;
import com.budgetmanager.model.User;
import com.budgetmanager.util.AuthUi;
import com.budgetmanager.util.DbExecutor;
import com.budgetmanager.util.FlashMessage;
import com.budgetmanager.util.Hexagon;
import com.budgetmanager.util.Icons;
import com.budgetmanager.util.LoginGuard;
import com.budgetmanager.util.PasswordUtil;
import com.budgetmanager.util.SessionManager;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Optional;

/** Écran de connexion : pseudo ou email + mot de passe. */
public class LoginController {

    private static final Logger log = LoggerFactory.getLogger(LoginController.class);

    @FXML private TextField     champEmail;
    @FXML private PasswordField champMotDePasse;
    @FXML private Button        boutonConnexion;
    @FXML private Hyperlink     lienInscription;
    @FXML private Hyperlink     lienMotDePasseOublie;
    @FXML private Label         labelMessage;
    @FXML private StackPane     zoneLogo;
    @FXML private StackPane     zoneIconeEmail;
    @FXML private StackPane     zoneIconeMotDePasse;
    @FXML private StackPane     zoneIconeSecurite;
    @FXML private StackPane     racine;

    private final UserDAO userDAO = new UserDAO();

    @FXML
    public void initialize() {
        // Badge logo hexagonal
        zoneLogo.getChildren().add(Hexagon.badge(46, Icons::wallet, true));

        // Icônes champs — blanches pour le thème glassmorphism
        javafx.scene.paint.Color couleurIcone = javafx.scene.paint.Color.web("#9dc8ff");
        zoneIconeEmail.getChildren().add(Icons.user(17, couleurIcone));
        zoneIconeMotDePasse.getChildren().add(Icons.lock(17, couleurIcone));

        // Icône sécurité bas de carte
        if (zoneIconeSecurite != null) {
            zoneIconeSecurite.getChildren().add(Icons.shieldCheck(14, javafx.scene.paint.Color.web("#7eb8ff", 0.8)));
        }

        AuthUi.activerFocusSurLignesPremium(racine);

        AuthUi.effacer(labelMessage);
        String flash = FlashMessage.consommer();
        if (flash != null) AuthUi.succes(labelMessage, flash);

        javafx.application.Platform.runLater(() -> champEmail.requestFocus());
    }

    @FXML
    public void seConnecter() {
        String identifiant = champEmail.getText().trim();
        String motDePasse  = champMotDePasse.getText();

        if (identifiant.isEmpty() || motDePasse.isEmpty()) {
            AuthUi.erreur(labelMessage, "Veuillez renseigner votre pseudo ou email, et votre mot de passe.");
            return;
        }

        long attente = LoginGuard.PARTAGE.secondesRestantes(identifiant);
        if (attente > 0) {
            AuthUi.erreur(labelMessage, "Trop de tentatives. Réessayez dans " + attente + " secondes.");
            champMotDePasse.clear();
            return;
        }

        // Désactiver le bouton pendant la vérification async.
        boutonConnexion.setDisable(true);
        AuthUi.effacer(labelMessage);

        // Point 4 : vérification DB + hachage PBKDF2 exécutés en arrière-plan
        // pour ne pas figer l'animation de l'écran de connexion.
        Task<Optional<User>> tache = new Task<>() {
            @Override
            protected Optional<User> call() {
                Optional<User> utilisateur = userDAO.trouverParIdentifiant(identifiant);
                if (utilisateur.isEmpty()) return Optional.empty();

                User user = utilisateur.get();
                // Vérification PBKDF2 (CPU-intensive) sur le thread de fond.
                if (!PasswordUtil.verifier(motDePasse, user.getMotDePasse())) {
                    return Optional.empty();
                }
                // Re-hachage si nécessaire (aussi CPU-intensive).
                if (PasswordUtil.doitEtreRehache(user.getMotDePasse())) {
                    String hash = PasswordUtil.hacher(motDePasse);
                    if (userDAO.changerMotDePasse(user.getIdUser(), hash)) {
                        user.setMotDePasse(hash);
                    }
                }
                return Optional.of(user);
            }
        };

        tache.setOnSucceeded(evt -> {
            boutonConnexion.setDisable(false);
            Optional<User> resultat = tache.getValue();

            if (resultat.isEmpty()) {
                // Distinguer DB éteinte de mauvais identifiants.
                if (!userDAO.baseAccessible()) {
                    AuthUi.erreur(labelMessage,
                            "Base de données inaccessible. Vérifiez que MySQL (WampServer) est démarré.");
                } else {
                    LoginGuard.PARTAGE.echec(identifiant);
                    AuthUi.erreur(labelMessage, "Pseudo, email ou mot de passe incorrect.");
                    champMotDePasse.clear();
                }
                return;
            }

            LoginGuard.PARTAGE.reussite(identifiant);
            SessionManager.connecter(resultat.get());
            try {
                Main.changerScene("view/dashboard.fxml", 1140, 740);
            } catch (IOException e) {
                AuthUi.erreur(labelMessage, "Impossible d'ouvrir le tableau de bord.");
                log.error("Navigation dashboard échouée", e);
            }
        });

        tache.setOnFailed(evt -> {
            boutonConnexion.setDisable(false);
            log.error("Connexion échouée", tache.getException());
            AuthUi.erreur(labelMessage, "Une erreur inattendue s'est produite. Veuillez réessayer.");
        });

        DbExecutor.soumettre(tache);
    }

    @FXML
    public void allerVersMotDePasseOublie() {
        try {
            Main.changerScene("view/forgot-password.fxml", Main.LARGEUR_AUTH, Main.HAUTEUR_AUTH);
        } catch (IOException e) {
            AuthUi.erreur(labelMessage, "Impossible d'ouvrir la page de récupération.");
        }
    }

    @FXML
    public void allerVersInscription() {
        log.info("Navigation vers inscription demandée");
        try {
            Main.changerScene("view/register.fxml", Main.LARGEUR_AUTH, Main.HAUTEUR_AUTH);
        } catch (IOException e) {
            log.error("Impossible d'ouvrir la page d'inscription", e);
            AuthUi.erreur(labelMessage, "Impossible d'ouvrir la page d'inscription.");
        }
    }

    @FXML
    public void allerVersConnexion() {
        log.info("Navigation vers connexion demandée");
        try {
            Main.changerScene("view/login.fxml", Main.LARGEUR_AUTH, Main.HAUTEUR_AUTH);
        } catch (IOException e) {
            log.error("Impossible d'ouvrir la page de connexion", e);
            AuthUi.erreur(labelMessage, "Impossible d'ouvrir la page de connexion.");
        }
    }
}
