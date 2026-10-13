package com.budgetmanager.controller;

import com.budgetmanager.Main;
import com.budgetmanager.dao.UserDAO;
import com.budgetmanager.model.User;
import com.budgetmanager.util.AuthUi;
import com.budgetmanager.util.FlashMessage;
import com.budgetmanager.util.Hexagon;
import com.budgetmanager.util.Icons;
import com.budgetmanager.util.OeilMotDePasse;
import com.budgetmanager.util.PasswordUtil;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.io.IOException;
import java.util.Optional;

/**
 * Récupération du mot de passe par question de sécurité, en trois étapes :
 * <ol>
 *   <li>saisie de l'email du compte ;</li>
 *   <li>réponse à la question de sécurité choisie à l'inscription ;</li>
 *   <li>choix du nouveau mot de passe.</li>
 * </ol>
 * La dernière étape n'est atteignable qu'après une réponse correcte, et la
 * réponse est limitée à {@value #TENTATIVES_MAX} essais.
 */
public class ForgotPasswordController {

    private static final int TENTATIVES_MAX = 3;
    private static final int LONGUEUR_MIN_MOT_DE_PASSE = PasswordUtil.LONGUEUR_MIN;

    @FXML private StackPane racine;
    @FXML private StackPane zoneLogo;
    @FXML private Label labelMessage;
    @FXML private Hyperlink lienRetour;

    // Indicateur d'étapes
    @FXML private Label pastille1;
    @FXML private Label pastille2;
    @FXML private Label pastille3;

    // Étape 1 : email
    @FXML private VBox panneauEmail;
    @FXML private TextField champEmail;
    @FXML private Button boutonContinuer;
    @FXML private StackPane zoneIconeEmail;

    // Étape 2 : question de sécurité
    @FXML private VBox panneauQuestion;
    @FXML private Label labelQuestion;
    @FXML private TextField champReponse;
    @FXML private Button boutonVerifier;
    @FXML private StackPane zoneIconeReponse;

    // Étape 3 : nouveau mot de passe
    @FXML private VBox panneauMotDePasse;
    @FXML private PasswordField champNouveauMotDePasse;
    @FXML private PasswordField champConfirmation;
    @FXML private Button boutonEnregistrer;
    @FXML private StackPane zoneIconeNouveau;
    @FXML private StackPane zoneIconeConfirmation;

    private final UserDAO userDAO = new UserDAO();

    private User utilisateurEnCours;
    private boolean reponseVerifiee;
    private int tentativesRestantes = TENTATIVES_MAX;

    @FXML
    public void initialize() {
        zoneLogo.getChildren().add(Hexagon.badge(46, Icons::shield, true));
        zoneIconeEmail.getChildren().add(Icons.mail(17, AuthUi.couleurIconeChamp()));
        zoneIconeReponse.getChildren().add(Icons.shield(17, AuthUi.couleurIconeChamp()));
        zoneIconeNouveau.getChildren().add(Icons.lock(17, AuthUi.couleurIconeChamp()));
        zoneIconeConfirmation.getChildren().add(Icons.lock(17, AuthUi.couleurIconeChamp()));
        lienRetour.setGraphic(Icons.arrowLeft(15, Color.web("#5daaff")));
        OeilMotDePasse.installer(champNouveauMotDePasse);
        OeilMotDePasse.installer(champConfirmation);
        AuthUi.activerFocusSurLignesPremium(racine);

        AuthUi.effacer(labelMessage);
        afficherEtape(1);
    }

    /* ------------------------------ Étape 1 ------------------------------ */

    @FXML
    public void continuer() {
        AuthUi.effacer(labelMessage);
        String email = champEmail.getText().trim();
        if (email.isEmpty()) {
            AuthUi.erreur(labelMessage, "Saisissez le pseudo ou l'email de votre compte.");
            return;
        }

        Optional<User> trouve = userDAO.trouverParIdentifiant(email);
        if (trouve.isEmpty() && !userDAO.baseAccessible()) {
            AuthUi.erreur(labelMessage, "Base de données inaccessible. Vérifiez que MySQL (WampServer) est démarré.");
            return;
        }
        if (trouve.isEmpty()) {
            AuthUi.erreur(labelMessage, "Aucun compte ne correspond à ce pseudo ou à cet email.");
            return;
        }
        if (!trouve.get().aUneQuestionSecurite()) {
            AuthUi.erreur(labelMessage, "Ce compte n'a pas de question de sécurité. Connectez-vous avec votre "
                    + "mot de passe actuel, puis ajoutez-en une dans Paramètres.");
            return;
        }

        utilisateurEnCours = trouve.get();
        reponseVerifiee = false;
        tentativesRestantes = TENTATIVES_MAX;
        labelQuestion.setText(utilisateurEnCours.getQuestionSecurite());
        champReponse.clear();
        afficherEtape(2);
        champReponse.requestFocus();
    }

    /* ------------------------------ Étape 2 ------------------------------ */

    @FXML
    public void verifierReponse() {
        AuthUi.effacer(labelMessage);
        if (utilisateurEnCours == null) {
            reinitialiser("Veuillez d'abord saisir votre email.");
            return;
        }
        if (PasswordUtil.normaliserReponse(champReponse.getText()).isEmpty()) {
            AuthUi.erreur(labelMessage, "Saisissez votre réponse.");
            return;
        }

        if (PasswordUtil.verifierReponse(champReponse.getText(), utilisateurEnCours.getReponseSecurite())) {
            reponseVerifiee = true;
            afficherEtape(3);
            champNouveauMotDePasse.requestFocus();
            return;
        }

        tentativesRestantes--;
        champReponse.clear();
        if (tentativesRestantes <= 0) {
            reinitialiser("Trop de réponses incorrectes. Pour votre sécurité, recommencez depuis l'étape 1.");
        } else {
            AuthUi.erreur(labelMessage, "Réponse incorrecte. Il vous reste " + tentativesRestantes
                    + (tentativesRestantes > 1 ? " essais." : " essai."));
        }
    }

    /* ------------------------------ Étape 3 ------------------------------ */

    @FXML
    public void enregistrerNouveauMotDePasse() {
        AuthUi.effacer(labelMessage);
        if (!reponseVerifiee || utilisateurEnCours == null) {
            reinitialiser("Vérifiez d'abord votre réponse de sécurité.");
            return;
        }

        String nouveau = champNouveauMotDePasse.getText();
        String confirmation = champConfirmation.getText();

        if (nouveau.length() < LONGUEUR_MIN_MOT_DE_PASSE) {
            AuthUi.erreur(labelMessage, "Le mot de passe doit contenir au moins " + LONGUEUR_MIN_MOT_DE_PASSE + " caractères.");
            return;
        }
        if (!nouveau.equals(confirmation)) {
            AuthUi.erreur(labelMessage, "Les deux mots de passe ne correspondent pas.");
            return;
        }

        if (userDAO.changerMotDePasse(utilisateurEnCours.getIdUser(), PasswordUtil.hacher(nouveau))) {
            FlashMessage.definir("Mot de passe modifié. Connectez-vous avec votre nouveau mot de passe.");
            allerVersConnexion();
        } else {
            AuthUi.erreur(labelMessage, "Impossible de modifier le mot de passe. Veuillez réessayer.");
        }
    }

    /* ------------------------------ Navigation ------------------------------ */

    @FXML
    public void allerVersConnexion() {
        try {
            Main.changerScene("view/login.fxml", Main.LARGEUR_AUTH, Main.HAUTEUR_AUTH);
        } catch (IOException e) {
            AuthUi.erreur(labelMessage, "Impossible d'ouvrir la page de connexion.");
        }
    }

    /* ------------------------------ Interne ------------------------------ */

    private void reinitialiser(String messageErreur) {
        utilisateurEnCours = null;
        reponseVerifiee = false;
        tentativesRestantes = TENTATIVES_MAX;
        champReponse.clear();
        champNouveauMotDePasse.clear();
        champConfirmation.clear();
        afficherEtape(1);
        AuthUi.erreur(labelMessage, messageErreur);
    }

    /** Affiche le panneau de l'étape demandée et met à jour l'indicateur (étapes faites = coche). */
    private void afficherEtape(int etape) {
        basculer(panneauEmail, etape == 1);
        basculer(panneauQuestion, etape == 2);
        basculer(panneauMotDePasse, etape == 3);

        Label[] pastilles = { pastille1, pastille2, pastille3 };
        for (int i = 0; i < pastilles.length; i++) {
            int numero = i + 1;
            Label p = pastilles[i];
            p.getStyleClass().removeAll("auth-step-active", "auth-step-done");
            if (numero < etape) {
                p.getStyleClass().add("auth-step-done");
                p.setText("");
                p.setGraphic(Icons.check(13, Color.web("#0b2a6b")));
            } else {
                p.setGraphic(null);
                p.setText(String.valueOf(numero));
                if (numero == etape) p.getStyleClass().add("auth-step-active");
            }
        }
    }

    private void basculer(VBox panneau, boolean visible) {
        panneau.setVisible(visible);
        panneau.setManaged(visible);
    }
}
