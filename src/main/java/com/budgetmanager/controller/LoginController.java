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
import com.budgetmanager.util.LoginGuard;
import com.budgetmanager.util.PasswordUtil;
import com.budgetmanager.util.SessionManager;
import com.budgetmanager.util.ViewCache;
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

    /** Roue posée dans le bouton pendant que la base répond, et son animation. */
    private javafx.animation.RotateTransition roueConnexion;
    /** Libellé d'origine du bouton, lu à l'affichage pour ne pas le dupliquer ici. */
    private String libelleConnexion;

    @FXML
    public void initialize() {
        try {
            zoneLogo.getChildren().add(Hexagon.badge(46, Icons::wallet, true));
            zoneIconeEmail.getChildren().add(Icons.user(17, AuthUi.couleurIconeChamp()));
            zoneIconeMotDePasse.getChildren().add(Icons.lock(17, AuthUi.couleurIconeChamp()));
            OeilMotDePasse.installer(champMotDePasse);
            if (zoneIconeSecurite != null) {
                zoneIconeSecurite.getChildren().add(Icons.shieldCheck(14, javafx.scene.paint.Color.web("#7eb8ff", 0.8)));
            }
            AuthUi.activerFocusSurLignesPremium(racine);
        } catch (Exception e) {
            log.error("Erreur initialize UI", e);
        }

        AuthUi.effacer(labelMessage);
        libelleConnexion = boutonConnexion.getText();
        String flash = FlashMessage.consommer();
        if (flash != null) AuthUi.succes(labelMessage, flash);

        racine.heightProperty().addListener((obs, avant, hauteur) -> serrer(hauteur.doubleValue()));
        javafx.application.Platform.runLater(() -> {
            serrer(racine.getHeight());
            champEmail.requestFocus();
        });
    }

    /** En dessous de cette hauteur, le pied de la carte passerait sous le bord de l'écran. */
    private static final double HAUTEUR_CONFORT = 780;

    private void serrer(double hauteur) {
        if (hauteur <= 0) return;
        boolean serree = hauteur < HAUTEUR_CONFORT;
        if (serree == racine.getStyleClass().contains("serree")) return;
        if (serree) racine.getStyleClass().add("serree");
        else racine.getStyleClass().remove("serree");
    }

    /**
     * Remplace le libellé du bouton par une roue blanche le temps que la base réponde,
     * puis le rend. Le cadre fait la hauteur d'une ligne de texte : la carte ne saute pas.
     */
    private void afficherRoue(boolean enTravail) {
        if (enTravail) {
            // La roue se taille sur la hauteur de la ligne de texte qu'elle remplace :
            // sinon le bouton grandit d'un coup quand la carte passe en mode serré.
            javafx.scene.text.Text mesure = new javafx.scene.text.Text(libelleConnexion);
            mesure.setFont(boutonConnexion.getFont());
            double ligne = Math.ceil(mesure.getLayoutBounds().getHeight());
            double diametre = Math.min(20, ligne);

            javafx.scene.shape.Arc arc = new javafx.scene.shape.Arc(0, 0,
                    diametre / 2 - 1.2, diametre / 2 - 1.2, 0, 265);
            // Un Arc se remplit par défaut en blanc : sans retrait, la roue serait un disque.
            arc.setFill(null);
            arc.setStroke(javafx.scene.paint.Color.WHITE);
            arc.setStrokeWidth(Math.max(1.8, diametre * 0.11));
            arc.setStrokeLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
            roueConnexion = new javafx.animation.RotateTransition(
                    javafx.util.Duration.millis(750), arc);
            roueConnexion.setByAngle(360);
            roueConnexion.setCycleCount(javafx.animation.Animation.INDEFINITE);
            roueConnexion.setInterpolator(javafx.animation.Interpolator.LINEAR);
            roueConnexion.play();

            StackPane cadre = new StackPane(arc);
            cadre.setMinSize(diametre, ligne);
            cadre.setPrefSize(diametre, ligne);
            boutonConnexion.setText("");
            boutonConnexion.setGraphic(cadre);
            boutonConnexion.getStyleClass().add("bouton-en-travail");
            return;
        }
        if (roueConnexion != null) {
            roueConnexion.stop();
            roueConnexion = null;
        }
        boutonConnexion.setGraphic(null);
        boutonConnexion.getStyleClass().remove("bouton-en-travail");
        boutonConnexion.setText(libelleConnexion);
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

        boutonConnexion.setDisable(true);
        afficherRoue(true);
        AuthUi.effacer(labelMessage);

        Task<Optional<User>> tache = new Task<>() {
            @Override
            protected Optional<User> call() throws Exception {
                Optional<User> utilisateur = userDAO.trouverParIdentifiant(identifiant);
                if (utilisateur.isEmpty()) {
                    // Test réseau effectué ici (thread de fond) : isValid()
                    // peut attendre plusieurs secondes, jamais sur l'UI thread.
                    if (!userDAO.baseAccessible()) {
                        throw new IllegalStateException(
                                "Base de données inaccessible. Vérifiez que MySQL (WampServer) est démarré.");
                    }
                    return Optional.empty();
                }

                User user = utilisateur.get();
                if (!PasswordUtil.verifier(motDePasse, user.getMotDePasse())) {
                    return Optional.empty();
                }
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
            afficherRoue(false);
            Optional<User> resultat = tache.getValue();

            if (resultat.isEmpty()) {
                LoginGuard.PARTAGE.echec(identifiant);
                AuthUi.erreur(labelMessage, "Pseudo, email ou mot de passe incorrect.");
                champMotDePasse.clear();
                return;
            }

            LoginGuard.PARTAGE.reussite(identifiant);
            // On peut arriver ici sans déconnexion propre : la base a lâché, l'écran
            // d'erreur a renvoyé à la connexion, et un autre compte se connecte. Les
            // vues en cache appartiennent alors au compte précédent.
            ViewCache.vider();
            SessionManager.connecter(resultat.get());
            try {
                Main.changerScene("view/dashboard.fxml", Main.LARGEUR_APP, Main.HAUTEUR_APP);
            } catch (IOException e) {
                AuthUi.erreur(labelMessage, "Impossible d'ouvrir le tableau de bord.");
                log.error("Navigation dashboard échouée", e);
            }
        });

        tache.setOnFailed(evt -> {
            boutonConnexion.setDisable(false);
            afficherRoue(false);
            Throwable erreur = tache.getException();
            if (erreur instanceof IllegalStateException) {
                AuthUi.erreur(labelMessage, erreur.getMessage());
            } else {
                log.error("Connexion échouée", erreur);
                AuthUi.erreur(labelMessage, "Une erreur inattendue s'est produite. Veuillez réessayer.");
            }
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
        try {
            Main.changerScene("view/register.fxml", Main.LARGEUR_AUTH, Main.HAUTEUR_AUTH);
        } catch (IOException e) {
            AuthUi.erreur(labelMessage, "Impossible d'ouvrir la page d'inscription.");
        }
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
