# 🎵 DupScan

Detecteur de doublons exacts pour le telephone : scan de toutes les
memoires (interne + cartes SD detectees automatiquement, ou dossiers
personnalises), verification par hash SHA-256, etat des lieux de l'espace
recuperable, resultats en liste triee par gain decroissant.

**Lecture seule stricte** : DupScan ne modifie, ne deplace et ne supprime
aucun fichier. Il informe, tu decides.

## Donnees utilisateur

- Aucune donnee n'est modifiee : les fichiers scannes ne sont jamais ecrits.
- Rapport CSV exportable (groupe, taille, SHA-256, date, chemin) dans
  Android/data/fr.lethargicrunner.dupscan/files/.
- Filtre par extensions (defaut : MP3, vide = tous les fichiers).
- Memoires detectees automatiquement (interne + SD), selectionnables.

## Methode

1. Inventaire des fichiers (taille, chemin).
2. Regroupement par taille (des tailles differentes ne peuvent pas etre des
   doublons — evite ~99 % des lectures).
3. Hash SHA-256 uniquement des fichiers dont la taille est partagee.
4. Groupes de doublons exacts, tries par espace recuperable decroissant,
   exemplaire le plus ancien marque.

## Telecharger l'APK

Onglet **Actions** → dernier run **Build APK** → artefact **DupScan-APK**.

## Compiler dans GitHub Codespaces

Dans le terminal du Codespace :

    gradle wrapper --gradle-version 8.7
    ./gradlew assembleDebug

APK obtenu dans app/build/outputs/apk/debug/app-debug.apk.

## Feuille de route

- [x] V0.1 : doublons exacts (SHA-256), lecture seule, etat des lieux
- [x] V0.1.1 : scan de toutes les memoires, interface Material 3
- [ ] V0.2 : deduplication assistee (deplacement vers corbeille, jamais suppression)
- [ ] V0.3 : doublons « percus » (meme titre + duree a ±2 s, encodages differents)
- [ ] V0.4 : index local pour scans incrementaux
- [ ] V1.0 : arborescence de bibliotheque (Artiste/Album) et tri automatique
