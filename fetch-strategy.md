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