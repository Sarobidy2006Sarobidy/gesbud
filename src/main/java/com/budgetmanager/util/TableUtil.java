package com.budgetmanager.util;

import com.budgetmanager.model.User;
import javafx.geometry.Pos;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;

/**
 * Utilitaires d'affichage centralisés pour les TableView.
 *
 * <p>Toutes les colonnes de montant de l'application passent par
 * {@link #formaterMontants} — changer le formatage ici suffit
 * pour que toute l'application soit cohérente.
 */
public final class TableUtil {

    private TableUtil() {}

    /**
     * Applique un CellFactory qui :
     * <ul>
     *   <li>Formate le montant avec la devise de l'utilisateur connecté</li>
     *   <li>Aligne le texte à droite</li>
     *   <li>Colore en vert les valeurs positives, en rouge les négatives</li>
     * </ul>
     */
    public static <T> void formaterMontants(TableColumn<T, Double> colonne) {
        colonne.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(Double montant, boolean vide) {
                super.updateItem(montant, vide);
                if (vide || montant == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                User utilisateur = SessionManager.getUtilisateurConnecte();
                String devise = (utilisateur != null && utilisateur.getDevise() != null)
                        ? utilisateur.getDevise() : "Ar";

                setText(CurrencyUtil.formater(montant, devise));
                setAlignment(Pos.CENTER_RIGHT);

                // Couleur selon le signe du montant.
                if (montant < 0) {
                    setStyle("-fx-text-fill: #d92d20;"); // rouge
                } else {
                    setStyle("");
                }
            }
        });
    }

    /**
     * CellFactory pour les colonnes de montant dans l'historique,
     * avec coloration contextuelle selon le type d'opération.
     * Type "Revenu" → vert, "Dépense"/"Économie" → rouge/neutre.
     */
    public static <T> void formaterMontantsAvecType(
            TableColumn<T, Double> colonne,
            java.util.function.Function<T, String> typeExtracteur) {

        colonne.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(Double montant, boolean vide) {
                super.updateItem(montant, vide);
                if (vide || montant == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                User utilisateur = SessionManager.getUtilisateurConnecte();
                String devise = (utilisateur != null && utilisateur.getDevise() != null)
                        ? utilisateur.getDevise() : "Ar";

                setText(CurrencyUtil.formater(montant, devise));
                setAlignment(Pos.CENTER_RIGHT);

                // Couleur selon le type de l'opération.
                T item = getTableView().getItems().get(getIndex());
                String type = typeExtracteur.apply(item);
                if ("Revenu".equals(type)) {
                    setStyle("-fx-text-fill: #12805c;"); // vert succès
                } else if ("Dépense".equals(type)) {
                    setStyle("-fx-text-fill: #d92d20;"); // rouge
                } else {
                    setStyle("-fx-text-fill: #1565c0;"); // bleu pour économies
                }
            }
        });
    }
}
