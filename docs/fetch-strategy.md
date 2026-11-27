# Fetch strategy

| Association | Fetch | Cascade | Justification |
|---|---|---|---|
| Contrat -> Paiement | LAZY | ALL + orphanRemoval | payments depend on the contract |
| Agence -> Vehicule | LAZY | none | vehicles stay if the agency is deleted |
| Agence -> Employe | LAZY | none | employees stay if the agency is deleted |
| Vehicule - Equipement | LAZY | none | equipment is shared, Set |
| Client -> Reservation | LAZY | PERSIST | keep reservations if client is deleted |
| Reservation -> Vehicule | LAZY | none | vehicle is independent |
| Reservation - Contrat | LAZY | ALL | contract depends on the reservation |
| Vehicule -> Maintenance | LAZY | PERSIST | keep maintenance history |

## LAZY vs EAGER comparison (Contrat -> Paiement)

- With LAZY, reading a contract runs one `select` on `contrat`. The `paiement` table is only queried when `getPaiements()` is called inside an open transaction.
- With EAGER, every read of a contract also loads all its payments, even when the page does not use them.
- Reading a LAZY collection after the transaction is closed throws `LazyInitializationException`. The fix is not EAGER but an explicit fetch: `ContratRepository` uses `@EntityGraph(attributePaths = {"paiements", ...})` for the contract list, and the services build DTOs inside the transaction.

Final choice: LAZY on every association.
