package com.budgetmanager.controller;

import com.budgetmanager.dao.UserDAO;
import com.budgetmanager.model.User;
import com.budgetmanager.util.AlertUtil;
import com.budgetmanager.util.Icons;
import com.budgetmanager.util.OeilMotDePasse;
import com.budgetmanager.util.PasswordUtil;
import com.budgetmanager.util.PseudoUtil;
import com.budgetmanager.util.RaccourcisClavier;
import com.budgetmanager.util.SecurityQuestions;
import com.budgetmanager.util.SessionManager;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.paint.Color;

import java.util.Optional;

public class SettingsController implements RaccourcisClavier {

    @FXML private TextField champNom;
    @FXML private TextField champPrenom;
    @FXML private TextField champPseudo;
    @FXML private TextField champEmail;
    @FXML private ComboBox<String> comboDevise;

    @FXML private PasswordField champAncienMotDePasse;
    @FXML private PasswordField champNouveauMotDePasse;
    @FXML private PasswordField champConfirmationMotDePasse;

    @FXML private ComboBox<String> comboQuestion;
    @FXML private TextField champReponse;
    @FXML private PasswordField champMotDePasseQuestion;

    @FXML private Button boutonEnregistrerProfil;
    @FXML private Button boutonChangerMotDePasse;
    @FXML private Button boutonEnregistrerQuestion;

    /** Contrôleur de la barre latérale incluse (fx:id="sidebar" -> champ "sidebarController"). */
    @FXML private SidebarController sidebarController;

    private final UserDAO userDAO = new UserDAO();

    @FXML
    public void initialize() {
        sidebarController.setActive("parametres");

        OeilMotDePasse.installer(champAncienMotDePasse);
        OeilMotDePasse.installer(champNouveauMotDePasse);
        OeilMotDePasse.installer(champConfirmationMotDePasse);
        OeilMotDePasse.installer(champMotDePasseQuestion);

        boutonEnregistrerProfil.setGraphic(Icons.save(15, Color.WHITE));
        boutonChangerMotDePasse.setGraphic(Icons.key(15, Color.WHITE));
        boutonEnregistrerQuestion.setGraphic(Icons.shield(15, Color.WHITE));

        comboDevise.getItems().addAll("Ar", "EUR", "USD", "GBP");
        comboQuestion.getItems().addAll(SecurityQuestions.QUESTIONS);

        User utilisateur = SessionManager.getUtilisateurConnecte();
        if (utilisateur != null) {
            champNom.setText(utilisateur.getNom());
            champPrenom.setText(utilisateur.getPrenom());
            champPseudo.setText(utilisateur.getPseudo());
            champEmail.setText(utilisateur.getEmail());
            comboDevise.setValue(utilisateur.getDevise());
            comboQuestion.setValue(utilisateur.getQuestionSecurite());
        }
    }

    @FXML
    public void enregistrerProfil() {
        if (!com.budgetmanager.util.DbHeartbeat.ok()) return;
        User utilisateur = SessionManager.getUtilisateurConnecte();
        String nom = champNom.getText().trim();
        String prenom = champPrenom.getText().trim();
        String email = champEmail.getText().trim();
        String pseudo = champPseudo.getText().trim();

        // Contrôles faits AVANT de toucher au compte connecté : une erreur ne le laisse pas à moitié modifié.
        if (nom.isEmpty() || prenom.isEmpty() || email.isEmpty()) {
            AlertUtil.afficherErreur("Champs manquants", "Nom, prénom et email sont obligatoires.");
            return;
        }
        if (!pseudo.isEmpty()) {
            String erreurPseudo = PseudoUtil.valider(pseudo);
            if (erreurPseudo != null) {
                AlertUtil.afficherErreur("Pseudo invalide", erreurPseudo);
                return;
            }
            if (userDAO.pseudoDejaUtilise(pseudo, utilisateur.getIdUser())) {
                AlertUtil.afficherErreur("Pseudo déjà pris", "Ce pseudo est déjà utilisé. Choisissez-en un autre.");
                return;
            }
        }
        Optional<User> memeEmail = userDAO.trouverParEmail(email);
        if (memeEmail.isPresent() && memeEmail.get().getIdUser() != utilisateur.getIdUser()) {
            AlertUtil.afficherErreur("Email déjà utilisé", "Un autre compte utilise déjà cet email.");
            return;
        }

        String ancienNom = utilisateur.getNom(), ancienPrenom = utilisateur.getPrenom();
        String ancienEmail = utilisateur.getEmail(), ancienPseudo = utilisateur.getPseudo(), ancienneDevise = utilisateur.getDevise();

        // Changer de devise ne convertit rien : les montants sont seulement relus
        // avec un autre symbole. Sans cet avertissement, 1 000 000 Ar deviennent
        // 1 000 000 € et tous les totaux semblent vrais.
        if (!java.util.Objects.equals(ancienneDevise, comboDevise.getValue())
                && !AlertUtil.confirmer("Changer de devise",
                        "Les montants déjà enregistrés ne seront pas convertis : ils seront "
                      + "simplement affichés avec le nouveau symbole. Continuer ?")) {
            comboDevise.setValue(ancienneDevise);
        }

        utilisateur.setNom(nom);
        utilisateur.setPrenom(prenom);
        utilisateur.setEmail(email);
        utilisateur.setPseudo(pseudo.isEmpty() ? null : pseudo);
        utilisateur.setDevise(comboDevise.getValue());

        if (userDAO.mettreAJour(utilisateur)) {
            AlertUtil.afficherSucces("Profil mis à jour", "Vos informations ont été enregistrées.");
        } else {
            utilisateur.setNom(ancienNom);
            utilisateur.setPrenom(ancienPrenom);
            utilisateur.setEmail(ancienEmail);
            utilisateur.setPseudo(ancienPseudo);
            utilisateur.setDevise(ancienneDevise);
            AlertUtil.afficherErreur("Erreur", "Impossible de mettre à jour le profil.");
        }
    }

    /** Ctrl+S : même action que le bouton « Enregistrer » du profil. */
    @Override
    public void auRaccourciEnregistrer() { enregistrerProfil(); }

    @FXML
    public void changerMotDePasse() {
        if (!com.budgetmanager.util.DbHeartbeat.ok()) return;
        User utilisateur = SessionManager.getUtilisateurConnecte();
        String ancien = champAncienMotDePasse.getText();
        String nouveau = champNouveauMotDePasse.getText();
        String confirmation = champConfirmationMotDePasse.getText();

        if (!PasswordUtil.verifier(ancien, utilisateur.getMotDePasse())) {
            AlertUtil.afficherErreur("Erreur", "Ancien mot de passe incorrect.");
            return;
        }
        if (nouveau.length() < PasswordUtil.LONGUEUR_MIN) {
            AlertUtil.afficherErreur("Erreur", "Le nouveau mot de passe doit contenir au moins " + PasswordUtil.LONGUEUR_MIN + " caractères.");
            return;
        }
        if (!nouveau.equals(confirmation)) {
            AlertUtil.afficherErreur("Erreur", "Le nouveau mot de passe ne correspond pas à la confirmation.");
            return;
        }

        String hash = PasswordUtil.hacher(nouveau);
        if (userDAO.changerMotDePasse(utilisateur.getIdUser(), hash)) {
            utilisateur.setMotDePasse(hash);
            AlertUtil.afficherSucces("Succès", "Mot de passe modifié avec succès.");
            champAncienMotDePasse.clear();
            champNouveauMotDePasse.clear();
            champConfirmationMotDePasse.clear();
        } else {
            AlertUtil.afficherErreur("Erreur", "Impossible de modifier le mot de passe.");
        }
    }

    /** Définit ou remplace la question de sécurité (mot de passe actuel exigé). */
    @FXML
    public void enregistrerQuestionSecurite() {
        if (!com.budgetmanager.util.DbHeartbeat.ok()) return;
        User utilisateur = SessionManager.getUtilisateurConnecte();
        String question = comboQuestion.getValue();
        String reponse = champReponse.getText();

        if (question == null || PasswordUtil.normaliserReponse(reponse).isEmpty()) {
            AlertUtil.afficherErreur("Erreur", "Choisissez une question et donnez-y une réponse.");
            return;
        }
        if (!PasswordUtil.verifier(champMotDePasseQuestion.getText(), utilisateur.getMotDePasse())) {
            AlertUtil.afficherErreur("Erreur", "Mot de passe actuel incorrect.");
            return;
        }

        String reponseHachee = PasswordUtil.hacherReponse(reponse);
        if (userDAO.definirQuestionSecurite(utilisateur.getIdUser(), question, reponseHachee)) {
            utilisateur.setQuestionSecurite(question);
            utilisateur.setReponseSecurite(reponseHachee);
            AlertUtil.afficherSucces("Question de sécurité", "Votre question de sécurité a été enregistrée.");
            champReponse.clear();
            champMotDePasseQuestion.clear();
        } else {
            AlertUtil.afficherErreur("Erreur", "Impossible d'enregistrer la question de sécurité.");
        }
    }

}
