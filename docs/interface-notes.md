# AutoLoc – REST API and web interface

## How to run it

1. Start MySQL, then run `AutolocApiApplication` from IntelliJ (profile `dev`, variable `DB_PASSWORD`).
2. Open http://localhost:8080 in a browser.
3. Sign in with one of the classroom accounts:
   - `student` / `student123` (role USER): **customer space**, rent a car and follow one's own reservations;
   - `admin` / `admin123` (role ADMIN): **agency space**, manage vehicles, agencies, customers, reservations and contracts.

The interface is made of three static files served by Spring Boot (`src/main/resources/static`): `index.html`, `css/app.css` and `js/app.js`. No extra tool (Node.js, npm) is needed. The JavaScript calls the REST API with `fetch` and sends the HTTP Basic credentials in the `Authorization` header of each request.

## Pages

### Customer space (USER)

| Page | Content |
|---|---|
| Louer une voiture | Catalogue of vehicles that are not in maintenance, with search and category filter. "Louer ce véhicule" asks who drives (one of the account's profiles, or a new profile typed in the same window) and the dates, and shows the price. |
| Mes réservations | The account's reservations, the driver's name and the status. A pending request can be cancelled. |
| Mes profils | One profile per driver (name, email, phone, licence number). An account can hold several profiles; they can be added, edited, and deleted when they have no reservation. |

Typical flow: the customer sends a request (status EN_ATTENTE), the agency confirms it (the vehicle becomes LOUE), creates the contract and records the payments.

### Agency space (ADMIN)

| Page | Content | ADMIN actions |
|---|---|---|
| Tableau de bord | Key figures (vehicles, agencies, customers, active reservations, contracts, amount collected), fleet status bar, upcoming reservations, contracts with an unpaid balance | none |
| Véhicules | Fleet table with search and status filter | add, edit, delete |
| Agences | Agencies with their number of vehicles | add, edit, delete |
| Clients | Customers with search | add, edit, delete |
| Réservations | Reservations with status filter and estimated price | create (with live price estimate), confirm, finish, cancel, create the contract, delete |
| Contrats | Contracts with payment progress and payment list | add a payment |

## Architecture

The code follows the layers of the course:

```
web.controller  ->  service  ->  repository  ->  domain (JPA entities)
      │               │
      └── web.dto ────┘
```

- **web.controller**: REST controllers (`/api/...`) and `ApiExceptionHandler` (turns exceptions into JSON errors).
- **web.dto**: Java records used for requests and responses. Entities are never returned directly, which avoids exposing the bidirectional associations (infinite JSON loops) and lazy-loading errors.
- **service**: business rules, each method is `@Transactional` (read-only by default). DTOs are built inside the transaction, so lazy associations can be read safely.
- **repository**: Spring Data JPA interfaces. List queries use `@EntityGraph` to load the associations they display in one query (for example the agency of each vehicle), instead of switching the mappings to EAGER. This keeps the LAZY strategy documented in `fetch-strategy.md`.

## REST endpoints

| Method | URL | Role | Description |
|---|---|---|---|
| POST | `/api/public/inscription` | public | Create a customer account |
| GET | `/api/me` | USER, ADMIN | Current user and roles (used by the login screen) |
| GET, POST | `/api/me/profils` | USER, ADMIN | The account's profiles / add a profile |
| PUT, DELETE | `/api/me/profils/{id}` | USER, ADMIN | Edit / delete one of the account's profiles |
| GET, POST | `/api/me/reservations` | USER, ADMIN | The account's reservations / new request, for an existing profile `{"clientId": 4, "vehiculeId": 2, "dateDebut": "2026-12-20", "dateFin": "2026-12-23"}` or with a new profile `{"nouveauClient": {...}, "vehiculeId": 2, ...}` |
| POST | `/api/me/reservations/{id}/annulation` | USER, ADMIN | Cancel one's own pending request |
| GET | `/api/vehicules`, `/api/vehicules/{id}` | USER, ADMIN | Catalogue / detail |
| POST, PUT, DELETE | `/api/vehicules`, `/api/vehicules/{id}` | ADMIN | Create / update / delete |
| GET | `/api/vehicules/{id}/photo` | public | Vehicle photo (404 if none) |
| POST, DELETE | `/api/vehicules/{id}/photo` | ADMIN | Upload (multipart field `photo`) / remove the photo |
| GET | `/api/agences`, `/api/agences/{id}` | USER, ADMIN | List / detail |
| POST, PUT, DELETE | `/api/agences`, `/api/agences/{id}` | ADMIN | Create / update / delete |
| GET | `/api/dashboard` | ADMIN | Key figures |
| GET | `/api/clients`, `/api/clients/{id}` | ADMIN | List / detail |
| POST, PUT, DELETE | `/api/clients`, `/api/clients/{id}` | ADMIN | Create / update / delete |
| GET | `/api/reservations`, `/api/reservations/{id}` | ADMIN | List / detail |
| POST | `/api/reservations` | ADMIN | Create a reservation (status EN_ATTENTE) |
| PATCH | `/api/reservations/{id}/statut` | ADMIN | Change the status, body `{"statut": "CONFIRMEE"}` |
| POST | `/api/reservations/{id}/contrat` | ADMIN | Create the contract of a confirmed or finished reservation |
| DELETE | `/api/reservations/{id}` | ADMIN | Delete a pending or cancelled reservation without contract |
| GET | `/api/contrats`, `/api/contrats/{id}` | ADMIN | Contracts with their payments |
| POST | `/api/contrats/{id}/paiements` | ADMIN | Add a payment, body `{"montant": 50, "modePaiement": "CARTE"}` |

Errors are returned as JSON: `{"status": 409, "message": "...", "details": [...]}`.

| Status | When |
|---|---|
| 400 | Invalid fields (Bean Validation: `@NotBlank`, `@Email`, `@DecimalMin`...) or unreadable JSON |
| 401 | Missing or wrong credentials |
| 403 | A USER calls an agency route (other customers' data, creation, update, deletion) |
| 404 | Unknown id |
| 409 | Business rule broken (see below) |

## Business rules

- A customer account owns one or several `Client` rows (profiles) through the new column `client.username`. The username always comes from the authentication (`Authentication.getName()`), never from the request body, so a customer can only read or change his own profile and reservations. Asking to cancel someone else's reservation answers 404, as if it did not exist.
- A request names exactly one driver: an existing profile of the account (`clientId`) or a new one (`nouveauClient`). A new profile and its reservation are saved in the same transaction, so a refused reservation does not leave a half-created profile. A customer cannot book a start date in the past.
- Input formats, checked by Bean Validation on the server (`ClientDto`) and repeated in the browser only to show the message under the field:
  - email: `^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$` ("Format d'email invalide."). `@Email` alone was not used because it accepts addresses such as `name@domain` without an extension;
  - licence number: `^[A-Za-z0-9]{8,15}$`, 8 to 15 letters or digits, no space or special character. It is stored in upper case.

- Registration numbers, customer emails and licence numbers are unique (checked before saving, the database constraint stays as a safety net).
- A reservation needs an end date on or after the start date, a vehicle that is not in maintenance, and no other active reservation (EN_ATTENTE or CONFIRMEE) on the same vehicle for an overlapping period.
- Allowed status changes: EN_ATTENTE → CONFIRMEE or ANNULEE; CONFIRMEE → TERMINEE or ANNULEE. Finished and cancelled reservations cannot change any more.
- Confirming a reservation sets the vehicle to LOUE. Finishing or cancelling a confirmed reservation sets it back to DISPONIBLE.
- A contract can only be created for a confirmed or finished reservation, once. Its amount is the daily rate × the number of days (first and last day included).
- A payment cannot exceed the remaining balance. Payments are saved through the contract (`CascadeType.ALL` on `Contrat.paiements`).
- Deletions follow the cascade choices of `fetch-strategy.md`: an agency with vehicles or employees, a customer with reservations, a vehicle with reservations or maintenance records, or a reservation with a contract cannot be deleted. The API answers 409 with an explanation instead of deleting related data.

Simplification: the vehicle status is a single value. If a vehicle has several confirmed reservations on different periods, finishing one of them sets it back to DISPONIBLE.

## Vehicle photos

- The agency adds a photo from the Véhicules page ("Photo" button). The customer catalogue shows it instead of the drawing.
- Photos are stored in MySQL, in their own table `vehicule_photo` (entity `VehiculePhoto`, primary key = vehicle id with `@MapsId`, so one photo per vehicle). The vehicle list never loads the image bytes: a projection query only reads which vehicles have a photo.
- Upload checks, on the server: 5 MB maximum (`spring.servlet.multipart.max-file-size`, plus a check in the service), and the format is detected from the first bytes of the file (JPEG, PNG or WebP). The file name and the Content-Type sent by the browser are not trusted, and SVG is refused because it can contain scripts. The browser repeats the size and type checks only to answer faster.
- `GET /api/vehicules/{id}/photo` is public: an `<img>` tag cannot send the Authorization header, and a car photo is not personal data. Responses are cached by the browser; the URL contains a version (`?v=...`) that changes with each new photo.

## Accounts in MySQL and sign-up

- The in-memory users of the security exercise (`InMemoryUserDetailsManager`) were replaced by accounts stored in MySQL: entity `Utilisateur` (table `utilisateur`: login, BCrypt hash, role USER or ADMIN). `CompteService` implements `UserDetailsService`, so Spring Security loads the account and its role from the database at each request. This answers the exercise question "What would be needed to load users and roles from MySQL?".
- The classroom accounts `student` / `student123` (USER) and `admin` / `admin123` (ADMIN) are created at startup by `ComptesDemoLoader` if they are missing, so the Postman tests (200, 401, 403) give the same results.
- `POST /api/public/inscription` `{"email": "...", "motDePasse": "..."}` creates a **customer** account. The role is never read from the request: nobody can sign up as ADMIN. Rules checked on the server: email format, 50 characters maximum, password of 8 to 72 characters with at least one letter and one digit (72 is the BCrypt limit). An email already used answers 409.
- The login page has a "Créer un compte" tab. After sign-up the customer is logged in directly, and the email of his account is pre-filled when he creates his first driver profile.

Limit: answering 409 for an existing email tells that this email has an account. A production site would send a confirmation email instead, and would also limit the number of attempts.

## Changes to the security configuration

- Static files (`/`, `/index.html`, `/css/**`, `/js/**`) and `/error` are public. All data stays under `/api/**` and stays protected.
- Rules, in order (Spring Security uses the first matching rule):
  1. `/api/admin/**`: ADMIN;
  2. `/api/me` and `/api/me/**`: USER and ADMIN (customer space, own data only);
  3. `GET /api/vehicules/**` and `GET /api/agences/**`: USER and ADMIN (public catalogue);
  4. `/api/dashboard`, `/api/clients/**`, `/api/reservations/**`, `/api/contrats/**`: ADMIN (data of all customers);
  5. any other `GET /api/**`: USER and ADMIN; any other method on `/api/**`: ADMIN.
- The rules of the security exercise (`/api/public/ping`, `/api/hello`, `/api/admin/report`) behave exactly as before: 200, 401, 403.
- **CSRF** is disabled for `/api/**` only, and sessions are stateless. CSRF attacks rely on the browser sending a session cookie automatically. Here the API keeps no session and the credentials are sent explicitly in the `Authorization` header by the JavaScript, so a third-party site cannot make the browser send them.
- Requests from the interface carry the header `X-Requested-With: XMLHttpRequest`. For those requests, a 401 is returned without the `WWW-Authenticate` header, so the browser does not open its own login pop-up on top of the login screen. Postman, curl and the browser address bar still receive the standard Basic challenge.

Limits (acceptable for the lab, not for production): the credentials are kept in the browser's `sessionStorage` for the tab, HTTP Basic sends them with every request, and the site runs over plain HTTP. A real deployment would use HTTPS, users stored in MySQL and a token or session-based login.
