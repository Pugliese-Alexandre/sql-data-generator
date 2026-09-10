import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Scanner;
import com.github.javafaker.Faker;
import java.util.Locale;
import java.util.Random;

public class Main {

static Faker faker = new Faker(new Locale("fr"));
    static Random random = new Random();

    public static void main(String[] args) {


        Scanner scanner = new Scanner(System.in);

        System.out.println("=== GENERATEUR DE REQUETES SQL ===");
        System.out.println();

        System.out.print("Entre le chemin du fichier JMerise (.mcd) : ");
        String chemin = scanner.nextLine();

        ArrayList<TableSQL> tables = lireTablesDepuisMCD(chemin);

        if (tables.size() == 0) {
            System.out.println("Aucune table detectee.");
            scanner.close();
            return;
        }

        System.out.println();
        System.out.println("Tables trouvees :");

        for (int i = 0; i < tables.size(); i++) {
            System.out.println((i + 1) + " - " + tables.get(i).nom);
        }

        System.out.print("Choisis le numero de la table : ");
        int choix = scanner.nextInt();

        if (choix < 1 || choix > tables.size()) {
            System.out.println("Choix invalide.");
            scanner.close();
            return;
        }

        TableSQL tableChoisie = tables.get(choix - 1);

        System.out.print("Combien de lignes veux-tu generer ? ");
        int nbLignes = scanner.nextInt();

        System.out.println();
        System.out.println("Table choisie : " + tableChoisie.nom);
        System.out.println("Colonnes detectees :");

        for (ColonneSQL c : tableChoisie.colonnes) {
            System.out.println("- " + c.nom
                    + " | type = " + c.type
                    + " | PK = " + c.primaryKey
                    + " | FK = " + c.foreignKey);
        }

        System.out.println();
        System.out.println("=== REQUETES GENEREES ===");

        for (int i = 0; i < nbLignes; i++) {
            System.out.println(creerInsert(tableChoisie, i));
        }

        scanner.close();
    }

    public static ArrayList<TableSQL> lireTablesDepuisMCD(String chemin) {

        ArrayList<TableSQL> tables = new ArrayList<>();

        try {
            File fichier = new File(chemin);

            if (!fichier.exists()) {
                System.out.println("Le fichier n'existe pas.");
                return tables;
            }

            FileInputStream fis = new FileInputStream(fichier);
            ObjectInputStream ois = new ObjectInputStream(fis);

            while (true) {
                try {
                    Object objet = ois.readObject();

                    if (objet instanceof ArrayList<?>) {
                        ArrayList<?> liste = (ArrayList<?>) objet;

                        for (Object element : liste) {
                            if (element != null) {
                                String nomClasse = element.getClass().getName();

                                if (nomClasse.contains("IhmEntite")) {
                                    TableSQL table = extraireTableDepuisIhmEntite(element);

                                    if (table != null && !table.nom.equals("") && table.colonnes.size() > 0) {
                                        ajouterSansDoublon(tables, table);
                                    }
                                }
                            }
                        }
                    }

                } catch (EOFException e) {
                    break;
                }
            }

            ois.close();
            fis.close();

        } catch (Exception e) {
            System.out.println("Erreur : " + e.getClass().getSimpleName());
            System.out.println("Message : " + e.getMessage());
        }

        return tables;
    }

    public static TableSQL extraireTableDepuisIhmEntite(Object ihmEntite) {

        try {
            Field champEntite = chercherChamp(ihmEntite.getClass(), "entite");

            if (champEntite == null) {
                return null;
            }

            champEntite.setAccessible(true);
            Object entite = champEntite.get(ihmEntite);

            if (entite == null) {
                return null;
            }

            String nomTable = lireChampString(entite, "nom");

            if (nomTable.equals("")) {
                return null;
            }

            TableSQL table = new TableSQL(nomTable);

            Field champListeAttributs = chercherChamp(entite.getClass(), "listeAttributs");

            if (champListeAttributs != null) {
                champListeAttributs.setAccessible(true);
                Object valeur = champListeAttributs.get(entite);

                if (valeur instanceof ArrayList<?>) {
                    ArrayList<?> listeAttributs = (ArrayList<?>) valeur;

                    for (Object attribut : listeAttributs) {
                        if (attribut != null) {
                            ColonneSQL colonne = extraireColonne(attribut);

                            if (colonne != null && !colonne.nom.equals("")) {
                                table.colonnes.add(colonne);
                            }
                        }
                    }
                }
            }

            return table;

        } catch (Exception e) {
            return null;
        }
    }

    public static ColonneSQL extraireColonne(Object attribut) {

        try {
            ColonneSQL colonne = new ColonneSQL();

            colonne.nom = lireChampString(attribut, "nom");
            colonne.type = lireChampString(attribut, "type");
            colonne.longueur = lireChampInt(attribut, "longueur");

            colonne.primaryKey = lireChampBoolean(attribut, "primaryKey");

            colonne.foreignKey = lireChampBoolean(attribut, "foreingKey");

            if (!colonne.foreignKey) {
                colonne.foreignKey = lireChampBoolean(attribut, "foreignKey");
            }

            return colonne;

        } catch (Exception e) {
            return null;
        }
    }

    public static Field chercherChamp(Class<?> classe, String nomChamp) {

        while (classe != null) {
            try {
                return classe.getDeclaredField(nomChamp);
            } catch (NoSuchFieldException e) {
                classe = classe.getSuperclass();
            }
        }

        return null;
    }

    public static String lireChampString(Object objet, String nomChamp) {

        try {
            Field champ = chercherChamp(objet.getClass(), nomChamp);

            if (champ != null) {
                champ.setAccessible(true);
                Object valeur = champ.get(objet);

                if (valeur instanceof String) {
                    return ((String) valeur).trim();
                }
            }

        } catch (Exception e) {
            // rien
        }

        return "";
    }

    public static boolean lireChampBoolean(Object objet, String nomChamp) {

        try {
            Field champ = chercherChamp(objet.getClass(), nomChamp);

            if (champ != null) {
                champ.setAccessible(true);
                Object valeur = champ.get(objet);

                if (valeur instanceof Boolean) {
                    return (Boolean) valeur;
                }
            }

        } catch (Exception e) {
            // rien
        }

        return false;
    }

    public static int lireChampInt(Object objet, String nomChamp) {

        try {
            Field champ = chercherChamp(objet.getClass(), nomChamp);

            if (champ != null) {
                champ.setAccessible(true);
                Object valeur = champ.get(objet);

                if (valeur instanceof Integer) {
                    return (Integer) valeur;
                }
            }

        } catch (Exception e) {
            // rien
        }

        return 0;
    }

    public static void ajouterSansDoublon(ArrayList<TableSQL> tables, TableSQL nouvelleTable) {

        for (TableSQL table : tables) {
            if (table.nom.equalsIgnoreCase(nouvelleTable.nom)) {
                return;
            }
        }

        tables.add(nouvelleTable);
    }

    public static String creerInsert(TableSQL table, int numeroLigne) {

        String partieColonnes = "";
        String partieValeurs = "";

        for (int i = 0; i < table.colonnes.size(); i++) {

            ColonneSQL colonne = table.colonnes.get(i);

            partieColonnes += colonne.nom;
            partieValeurs += genererValeur(colonne, numeroLigne);

            if (i < table.colonnes.size() - 1) {
                partieColonnes += ", ";
                partieValeurs += ", ";
            }
        }

        return "INSERT INTO " + table.nom + " (" + partieColonnes + ") VALUES (" + partieValeurs + ");";
    }

    public static String genererValeur(ColonneSQL colonne, int numeroLigne) {

        String nom = "";

        if (colonne.nom != null) {
            nom = colonne.nom.toLowerCase();
        }

        String type = "";

        if (colonne.type != null) {
            type = colonne.type.toLowerCase();
        }

        // CLE PRIMAIRE
        if (colonne.primaryKey || nom.equals("id") || nom.startsWith("id_")) {
            return "" + (numeroLigne + 1);
        }

        // CLE ETRANGERE
        if (colonne.foreignKey || nom.startsWith("fk_")) {
            return "" + (1 + random.nextInt(5));
        }

        // EMAIL
        if (nom.contains("email") || nom.contains("mail")) {
            return "'" + faker.internet().emailAddress() + "'";
        }

        // TELEPHONE
        if (nom.contains("telephone") || nom.contains("tel") || nom.contains("gsm")) {
            return "'" + faker.phoneNumber().cellPhone() + "'";
        }

        // PRENOM
        if (nom.contains("prenom") || nom.contains("prénom")) {
            return "'" + faker.name().firstName() + "'";
        }

        // NOM
        if (nom.equals("nom") || nom.contains("nom_") || nom.startsWith("nom")) {
            return "'" + faker.name().lastName() + "'";
        }

        // ADRESSE
        if (nom.contains("adresse") || nom.contains("rue")) {
            return "'" + faker.address().streetAddress() + "'";
        }

        // VILLE
        if (nom.contains("ville")) {
            return "'" + faker.address().city() + "'";
        }

        // CODE POSTAL
        if (nom.contains("codepostal")
                || nom.contains("code_postal")
                || nom.equals("cp")) {

            return "'" + faker.address().zipCode() + "'";
        }

        // DESCRIPTION
        if (nom.contains("description")
                || nom.contains("commentaire")
                || nom.contains("remarque")) {

            return "'" + faker.lorem().sentence() + "'";
        }

        // DATE
        if (type.contains("date") || nom.contains("date")) {
            return "'2026-05-" + String.format("%02d", 1 + random.nextInt(28)) + "'";
        }

        // PRIX / MONTANT
        if (nom.contains("prix")
                || nom.contains("montant")
                || nom.contains("salaire")) {

            return "" + (10 + random.nextInt(500));
        }

        // QUANTITE / STOCK
        if (nom.contains("quantite")
                || nom.contains("quantité")
                || nom.contains("stock")
                || nom.contains("nombre")) {

            return "" + (1 + random.nextInt(100));
        }

        // BOOLEAN
        if (type.contains("bool")) {
            return random.nextBoolean() ? "true" : "false";
        }

        // NOMBRE
        if (type.contains("int")
                || type.contains("number")
                || type.contains("numeric")
                || type.contains("decimal")) {

            return "" + (1 + random.nextInt(100));
        }

        // TEXTE GENERAL
        if (type.contains("char")
                || type.contains("varchar")
                || type.contains("text")
                || type.equals("")) {

            return "'" + faker.lorem().word() + "'";
        }

        // SECURITE
        return "'" + faker.lorem().word() + "'";
    }

    public static String genererTexteAleatoire(int longueur) {

        String lettres = "abcdefghijklmnopqrstuvwxyz";
        String resultat = "";

        for (int i = 0; i < longueur; i++) {
            int index = random.nextInt(lettres.length());
            resultat += lettres.charAt(index);
        }

        return resultat;
    }

    public static String genererNombreAleatoire(int longueur) {

        String chiffres = "0123456789";
        String resultat = "";

        for (int i = 0; i < longueur; i++) {
            int index = random.nextInt(chiffres.length());
            resultat += chiffres.charAt(index);
        }

        return resultat;
    }

    public static String genererPhraseAleatoire() {

        return genererTexteAleatoire(8) + " "
                + genererTexteAleatoire(6) + " "
                + genererTexteAleatoire(10);
    }

    static class TableSQL {
        String nom;
        ArrayList<ColonneSQL> colonnes;

        public TableSQL(String nom) {
            this.nom = nom;
            this.colonnes = new ArrayList<>();
        }
    }

    static class ColonneSQL {
        String nom;
        String type;
        boolean primaryKey;
        boolean foreignKey;
        int longueur;
    }
}