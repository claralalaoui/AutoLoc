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
| Louer une voiture | Catalogue of vehicles that are not in maintenance, with search and category filter. "Louer ce véhicule" asks for the dates and shows the price. |
| Mes réservations | The customer's own reservations and their status. A pending request can be cancelled. |
| Mon profil | Name, email, phone, licence number. Filled in once, before the first rental. |

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
| GET | `/api/me` | USER, ADMIN | Current user and roles (used by the login screen) |
| GET, PUT | `/api/me/profil` | USER, ADMIN | Own customer profile (404 until it is filled) |
| GET, POST | `/api/me/reservations` | USER, ADMIN | Own reservations / new request `{"vehiculeId": 2, "dateDebut": "2026-12-20", "dateFin": "2026-12-23"}` |
| POST | `/api/me/reservations/{id}/annulation` | USER, ADMIN | Cancel one's own pending request |
| GET | `/api/vehicules`, `/api/vehicules/{id}` | USER, ADMIN | Catalogue / detail |
| POST, PUT, DELETE | `/api/vehicules`, `/api/vehicules/{id}` | ADMIN | Create / update / delete |
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

- A customer account is linked to one `Client` row through the new column `client.username`. The username always comes from the authentication (`Authentication.getName()`), never from the request body, so a customer can only read or change his own profile and reservations. Asking to cancel someone else's reservation answers 404, as if it did not exist.
- A customer cannot book before filling his profile, nor book a start date in the past.

- Registration numbers, customer emails and licence numbers are unique (checked before saving, the database constraint stays as a safety net).
- A reservation needs an end date on or after the start date, a vehicle that is not in maintenance, and no other active reservation (EN_ATTENTE or CONFIRMEE) on the same vehicle for an overlapping period.
- Allowed status changes: EN_ATTENTE → CONFIRMEE or ANNULEE; CONFIRMEE → TERMINEE or ANNULEE. Finished and cancelled reservations cannot change any more.
- Confirming a reservation sets the vehicle to LOUE. Finishing or cancelling a confirmed reservation sets it back to DISPONIBLE.
- A contract can only be created for a confirmed or finished reservation, once. Its amount is the daily rate × the number of days (first and last day included).
- A payment cannot exceed the remaining balance. Payments are saved through the contract (`CascadeType.ALL` on `Contrat.paiements`).
- Deletions follow the cascade choices of `fetch-strategy.md`: an agency with vehicles or employees, a customer with reservations, a vehicle with reservations or maintenance records, or a reservation with a contract cannot be deleted. The API answers 409 with an explanation instead of deleting related data.

Simplification: the vehicle status is a single value. If a vehicle has several confirmed reservations on different periods, finishing one of them sets it back to DISPONIBLE.

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
