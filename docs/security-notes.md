# Security notes

## Route rules
- /api/public/** : everyone (permitAll)
- /api/admin/** : ADMIN only
- /api/** : USER or ADMIN
- anything else : must be logged in

The rules are checked in order, so /api/admin/** has to be before /api/**. Otherwise a USER could access the admin routes.

401 = not logged in, 403 = logged in but wrong role.

## password endoing
Passwords are encoded with BCrypt, so they are never stored in plain text. BCrypt is a one-way hash, so the original password can't be read back.

## In-memory users
The users (student and admin) are defined in SecurityConfig, only for this exercise. They are lost when the app restarts and can't be changed without changing the code. For a real app, users and roles should be stored in MySQL and loaded with a UserDetailsService.