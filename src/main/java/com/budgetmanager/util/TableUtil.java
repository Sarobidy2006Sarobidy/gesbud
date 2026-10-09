package com.budgetmanager.util;

import com.budgetmanager.model.User;
import javafx.css.PseudoClass;
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
 *
 * <p>La couleur est appliquée via des {@link PseudoClass} (et non {@code setStyle}),
 * gérées nativement par le fichier style.css. Contrairement à {@code setStyle} qui
 * force le moteur CSS à réanalyser une chaîne à chaque {@code updateItem} (donc à
 * chaque scroll), l'état de pseudo-classe ne recalcule le rendu que lorsque la
 * couleur change réellement — indispensable sur les tables de plusieurs centaines
 * de lignes.
 */
public final class TableUtil {

    private TableUtil() {}

    /** Pseudo-classes de coloration des montants, définies dans style.css. */
    private static final PseudoClass NEGATIF = PseudoClass.getPseudoClass("negatif");
    private static final PseudoClass POSITIF = PseudoClass.getPseudoClass("positif");
    private static final PseudoClass NEUTRE  = PseudoClass.getPseudoClass("neutre");

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
                    pseudoClassStateChanged(NEGATIF, false);
                    return;
                }
                User utilisateur = SessionManager.getUtilisateurConnecte();
                String devise = (utilisateur != null && utilisateur.getDevise() != null)
                        ? utilisateur.getDevise() : "Ar";

                setText(CurrencyUtil.formater(montant, devise));
                setAlignment(Pos.CENTER_RIGHT);

                // Couleur selon le signe du montant (gérée nativement par style.css).
                pseudoClassStateChanged(NEGATIF, montant < 0);
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
                    pseudoClassStateChanged(POSITIF, false);
                    pseudoClassStateChanged(NEGATIF, false);
                    pseudoClassStateChanged(NEUTRE, false);
                    return;
                }
                User utilisateur = SessionManager.getUtilisateurConnecte();
                String devise = (utilisateur != null && utilisateur.getDevise() != null)
                        ? utilisateur.getDevise() : "Ar";

                setText(CurrencyUtil.formater(montant, devise));
                setAlignment(Pos.CENTER_RIGHT);

                // Couleur selon le type d'opération (cellules réutilisées : on réinitialise tout).
                T item = getTableView().getItems().get(getIndex());
                String type = typeExtracteur.apply(item);
                boolean revenu  = "Revenu".equals(type);
                boolean depense = "Dépense".equals(type);
                pseudoClassStateChanged(POSITIF, revenu);
                pseudoClassStateChanged(NEGATIF, depense);
                pseudoClassStateChanged(NEUTRE, !revenu && !depense);
            }
        });
    }
}
