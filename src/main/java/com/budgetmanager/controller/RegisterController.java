package com.budgetmanager.controller;

import com.budgetmanager.Main;
import com.budgetmanager.dao.UserDAO;
import com.budgetmanager.model.User;
import com.budgetmanager.util.AuthUi;
import com.budgetmanager.util.DbExecutor;
import com.budgetmanager.util.FlashMessage;
import com.budgetmanager.util.Hexagon;
import com.budgetmanager.util.Icons;
import com.budgetmanager.util.OeilMotDePasse;
import com.budgetmanager.util.PasswordUtil;
import com.budgetmanager.util.PseudoUtil;
import com.budgetmanager.util.SecurityQuestions;
import com.budgetmanager.util.SessionManager;
import com.budgetmanager.util.ViewCache;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/** Écran d'inscription : identité, email, mot de passe, devise et question de sécurité. */
public class RegisterController {

    private static final Logger log = LoggerFactory.getLogger(RegisterController.class);

    private static final int LONGUEUR_MIN_MOT_DE_PASSE = PasswordUtil.LONGUEUR_MIN;

    @FXML private TextField champNom;
    @FXML private TextField champPrenom;
    @FXML private TextField champPseudo;
    @FXML private TextField champEmail;
    @FXML private PasswordField champMotDePasse;
    @FXML private PasswordField champConfirmationMotDePasse;
    @FXML private ComboBox<String> comboDevise;
    @FXML private ComboBox<String> comboQuestion;
    @FXML private TextField champReponse;
    @FXML private Button boutonInscription;
    @FXML private Label labelMessage;

    @FXML private StackPane racine;
    @FXML private StackPane zoneLogo;
    @FXML private StackPane zoneIconeNom;
    @FXML private StackPane zoneIconePrenom;
    @FXML private StackPane zoneIconePseudo;
    @FXML private StackPane zoneIconeEmail;
    @FXML private StackPane zoneIconeMotDePasse;
    @FXML private StackPane zoneIconeConfirmation;
    @FXML private StackPane zoneIconeDevise;
    @FXML private StackPane zoneIconeQuestion;
    @FXML private StackPane zoneIconeReponse;
    @FXML private StackPane zoneIconeSecurite;

    private final UserDAO userDAO = new UserDAO();

    @FXML
    public void initialize() {
        zoneLogo.getChildren().add(Hexagon.badge(46, Icons::wallet, true));
        zoneIconeNom.getChildren().add(Icons.user(17, AuthUi.couleurIconeChamp()));
        zoneIconePrenom.getChildren().add(Icons.user(17, AuthUi.couleurIconeChamp()));
        zoneIconePseudo.getChildren().add(Icons.user(17, AuthUi.couleurIconeChamp()));
        zoneIconeEmail.getChildren().add(Icons.mail(17, AuthUi.couleurIconeChamp()));
        zoneIconeMotDePasse.getChildren().add(Icons.lock(17, AuthUi.couleurIconeChamp()));
        zoneIconeConfirmation.getChildren().add(Icons.lock(17, AuthUi.couleurIconeChamp()));
        zoneIconeDevise.getChildren().add(Icons.dollar(17, AuthUi.couleurIconeChamp()));
        zoneIconeQuestion.getChildren().add(Icons.helpCircle(17, AuthUi.couleurIconeChamp()));
        zoneIconeReponse.getChildren().add(Icons.shield(17, AuthUi.couleurIconeChamp()));
        zoneIconeSecurite.getChildren().add(
                Icons.shieldCheck(14, javafx.scene.paint.Color.web("#7eb8ff", 0.8)));
        OeilMotDePasse.installer(champMotDePasse);
        OeilMotDePasse.installer(champConfirmationMotDePasse);
        champNom.setTextFormatter(com.budgetmanager.util.LongueurFieldFormatter.creer(50));
        champPrenom.setTextFormatter(com.budgetmanager.util.LongueurFieldFormatter.creer(50));
        champEmail.setTextFormatter(com.budgetmanager.util.LongueurFieldFormatter.creer(100));
        AuthUi.activerFocusSurLignesPremium(racine);

        comboDevise.getItems().addAll("Ar", "EUR", "USD", "GBP");
        comboDevise.getSelectionModel().selectFirst();
        comboQuestion.getItems().addAll(SecurityQuestions.QUESTIONS);
        AuthUi.styliserPrompt(comboQuestion);

        AuthUi.effacer(labelMessage);

        // Focus automatique sur le premier champ (Point 5).
        javafx.application.Platform.runLater(() -> champNom.requestFocus());
    }

    @FXML
    public void sInscrire() {
        String nom = champNom.getText().trim();
        String prenom = champPrenom.getText().trim();
        String pseudo = champPseudo.getText().trim();
        String email = champEmail.getText().trim();
        String motDePasse = champMotDePasse.getText();
        String confirmation = champConfirmationMotDePasse.getText();
        String devise = comboDevise.getValue();
        String question = comboQuestion.getValue();
        String reponse = champReponse.getText();

        if (nom.isEmpty() || prenom.isEmpty() || email.isEmpty() || motDePasse.isEmpty()) {
            AuthUi.erreur(labelMessage, "Nom, prénom, pseudo, email et mot de passe sont obligatoires.");
            return;
        }
        String erreurPseudo = PseudoUtil.valider(pseudo);
        if (erreurPseudo != null) {
            AuthUi.erreur(labelMessage, erreurPseudo);
            return;
        }
        if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            AuthUi.erreur(labelMessage, "L'adresse email n'est pas valide.");
            return;
        }
        if (motDePasse.length() < LONGUEUR_MIN_MOT_DE_PASSE) {
            AuthUi.erreur(labelMessage, "Le mot de passe doit contenir au moins " + LONGUEUR_MIN_MOT_DE_PASSE + " caractères.");
            return;
        }
        if (!motDePasse.equals(confirmation)) {
            AuthUi.erreur(labelMessage, "Les mots de passe ne correspondent pas.");
            return;
        }
        if (question == null || PasswordUtil.normaliserReponse(reponse).isEmpty()) {
            AuthUi.erreur(labelMessage, "Choisissez une question de sécurité et donnez-y une réponse.");
            return;
        }
        if (userDAO.trouverParEmail(email).isPresent()) {
            AuthUi.erreur(labelMessage, "Un compte existe déjà avec cet email.");
            return;
        }
        if (userDAO.pseudoDejaUtilise(pseudo, 0)) {
            AuthUi.erreur(labelMessage, "Ce pseudo est déjà pris. Choisissez-en un autre.");
            return;
        }

        // Point 4 : hachage PBKDF2 en arrière-plan — évite de geler l'UI pendant 1 seconde.
        boutonInscription.setDisable(true);
        AuthUi.effacer(labelMessage);

        final String reponseFinale = reponse;
        final String motDePasseFinal = motDePasse;
        Task<User> tache = new Task<>() {
            @Override
            protected User call() {
                User u = new User(nom, prenom, email,
                        PasswordUtil.hacher(motDePasseFinal), devise);
                u.setPseudo(pseudo);
                u.setQuestionSecurite(question);
                u.setReponseSecurite(PasswordUtil.hacherReponse(reponseFinale));
                // creer() renseigne l'ID généré sur l'objet même.
                return userDAO.creer(u) ? u : null;
            }
        };
        tache.setOnSucceeded(evt -> {
            boutonInscription.setDisable(false);
            User compteCree = tache.getValue();
            if (compteCree == null) {
                AuthUi.erreur(labelMessage, "L'inscription a échoué. Veuillez réessayer.");
                return;
            }
            // Connexion automatique : le mot de passe vient d'être saisi et
            // confirmé deux fois, inutile de le redemander immédiatement.
            ViewCache.vider();
            SessionManager.connecter(compteCree);
            try {
                Main.changerScene("view/dashboard.fxml", Main.LARGEUR_APP, Main.HAUTEUR_APP);
            } catch (IOException e) {
                log.error("Navigation dashboard après inscription échouée", e);
                FlashMessage.definir("Votre compte a été créé. Vous pouvez vous connecter.");
                allerVersConnexion();
            }
        });
        tache.setOnFailed(evt -> {
            boutonInscription.setDisable(false);
            log.error("Inscription échouée", tache.getException());
            AuthUi.erreur(labelMessage, "Une erreur inattendue s'est produite.");
        });
        DbExecutor.soumettre(tache);
    }

    @FXML
    public void allerVersConnexion() {
        try {
            Main.changerScene("view/login.fxml", Main.LARGEUR_AUTH, Main.HAUTEUR_AUTH);
        } catch (IOException e) {
            AuthUi.erreur(labelMessage, "Impossible d'ouvrir la page de connexion.");
        }
    }
}
