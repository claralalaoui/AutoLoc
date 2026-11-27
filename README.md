# AutoLoc

Application de location de voitures réalisée pour l'UP ASI (Architecture des Systèmes d'Information), ECE Paris 2026-2027.
Deux espaces : le **client** loue une voiture, l'**agence** gère la flotte, les réservations, les contrats et les paiements.

Équipe : Clara Lalaoui, Imane Allik.

Technologies : Java 17+, Spring Boot, Maven, Spring Data JPA (Hibernate), MySQL, Lombok, Spring Security, Actuator, Prometheus, Grafana (Docker), HTML / CSS / JavaScript.

## Lancer le projet

1. Démarrer MySQL (la base `autoloc_db` est créée automatiquement).
2. Dans IntelliJ, lancer `AutolocApiApplication` avec le profil `dev` et la variable d'environnement `DB_PASSWORD=<mot de passe root MySQL>`.
3. Ouvrir http://localhost:8080.
4. Monitoring : `docker compose up -d`, puis http://localhost:9090/targets (Prometheus) et http://localhost:3000 (Grafana).

Comptes de démonstration : `student` / `student123` (client) et `admin` / `admin123` (agence). On peut aussi créer un compte client depuis la page de connexion.

## Ce qui était demandé et où le trouver

### Workshop 1, session 1 : projet Spring Boot et entité Vehicule

| Demandé | Où | Commit |
|---|---|---|
| Projet Spring Boot / Maven avec Web, Data JPA, MySQL, Lombok, Validation, DevTools | `pom.xml` | `e908e56` |
| Connexion MySQL et propriétés Hibernate (`ddl-auto=update`, `show-sql`) | `application.properties`, `application-dev.properties` | `e908e56` |
| Mot de passe hors du fichier versionné (variable d'environnement) | `spring.datasource.password=${DB_PASSWORD}` | `e908e56` |
| Logs lisibles : INFO au démarrage, DEBUG pour le SQL | `logging.level...` dans les fichiers de propriétés | `e908e56` |
| Packages `domain`, `repository`, `service`, `web.controller`, `web.dto` | `src/main/java/fr/ece/autoloc` | `e908e56` |
| Entité `Vehicule` avec Lombok ciblé (`@Getter`, `@Setter`, pas de `@Data`) et enums `StatutVehicule`, `CategorieVehicule` | `domain/Vehicule.java` | `e908e56` |

### Devoir : les entités restantes

| Demandé | Où | Commit |
|---|---|---|
| 9 entités au total, `@GeneratedValue(IDENTITY)` | `domain/` | `6868f9f` |
| Enums `RoleEmploye`, `StatutReservation`, `ModePaiement` | `domain/` | `6868f9f` |
| Optionnel : `CommandLineRunner` qui insère des véhicules de démo | `config/DemoDataLoader.java` | `70397c9` |
| Optionnel : profil `dev` séparé | `application-dev.properties` | `70397c9` |

### Workshop 2 : associations, cascade et fetch

| Demandé | Où | Commit |
|---|---|---|
| Contrat ↔ Paiement bidirectionnel avec `mappedBy`, `CascadeType.ALL`, `orphanRemoval`, `LAZY` | `Contrat.java`, `Paiement.java` | `a7e858e` |
| Les deux côtés maintenus à l'ajout d'un paiement | `Contrat.addPaiement()` | `a7e858e` |
| Les 7 autres associations, dont la table de jointure `vehicule_equipement` | entités de `domain/` | `a7e858e` |
| Comparaison LAZY / EAGER | `docs/fetch-strategy.md` | |
| `docs/fetch-strategy.md` qui justifie chaque fetch et chaque cascade | `docs/fetch-strategy.md` | `a7e858e` |

### Spring Security

| Demandé | Où | Commit |
|---|---|---|
| Dépendance `spring-boot-starter-security` | `pom.xml` | `0ee35d8` |
| `SecurityConfig` avec `SecurityFilterChain` (routes publiques, authentifiées, ADMIN) | `config/SecurityConfig.java` | `0ee35d8` |
| Deux utilisateurs, rôles différents, mots de passe BCrypt | aujourd'hui en base MySQL : `CompteService`, `ComptesDemoLoader` | `0ee35d8`, puis évolution |
| Trois endpoints de test | `web/controller/SecurityDemoController.java` | `0ee35d8` |
| Captures Postman 200 / 401 / 403 | `docs/screenshots/` | |
| `docs/security-notes.md` (ordre des règles, 401 / 403, BCrypt, limites) | `docs/security-notes.md` | `0ee35d8` |

### Monitoring : Prometheus et Grafana

| Demandé | Où | Commit |
|---|---|---|
| Actuator et `micrometer-registry-prometheus` | `pom.xml` | `995bb69` |
| Endpoints `health` et `prometheus` exposés, autorisés dans Spring Security | `application.properties`, `SecurityConfig.java` | `995bb69` |
| `monitoring/prometheus.yml` et `docker-compose.yml` | racine du projet | `995bb69` |
| Capture de Prometheus Targets avec `autoloc-api` UP | `docs/screenshots/` | |
| Tableau de bord Grafana d'au moins 4 panneaux | « AutoLoc — Application Monitoring » : mémoire JVM, CPU, requêtes HTTP par seconde, threads | |
| `docs/monitoring-notes.md` : architecture, métriques, un problème résolu (cible DOWN en 401) | `docs/monitoring-notes.md` | `b9863aa` |

## Ce qui a été ajouté en plus du sujet

Le détail est dans `docs/interface-notes.md`.

- **API REST complète** en couches (repository, service, DTO, controller, gestion des erreurs en JSON) pour les véhicules, agences, clients, réservations, contrats et paiements. Les entités ne sont jamais renvoyées directement.
- **Règles métier** : pas de réservation qui chevauche une autre sur le même véhicule, cycle de vie d'une réservation (en attente, confirmée, terminée, annulée), contrat calculé à partir du tarif, paiement limité au reste à payer, suppressions refusées quand des données sont liées.
- **Interface web** servie par Spring Boot : espace agence (tableau de bord, gestion complète) et espace client (catalogue, location, suivi des réservations).
- **Comptes en base MySQL et inscription** : réponse à la dernière question de l'exercice sécurité. Un compte créé par inscription est toujours client.
- **Plusieurs profils conducteur** par compte ; un client ne voit que ses propres données.
- **Validation** côté serveur et côté navigateur : format de l'email, permis de 8 à 15 lettres ou chiffres, téléphone, mot de passe.
- **Photos des véhicules**, avec vérification du vrai format du fichier et taille limitée à 5 Mo.

## Captures à joindre

Dans `docs/screenshots/` :

- `postman-anonyme.png`, `postman-student.png`, `postman-admin.png` : les trois routes et leurs codes 200, 401, 403 ;
- `prometheus-targets-up.png` : la cible `autoloc-api` UP ;
- `grafana-dashboard.png` : le tableau de bord avec ses 4 panneaux.
