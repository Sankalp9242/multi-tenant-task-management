# Multi-Tenant Task Management System

A Spring Boot REST API for multiple organizations sharing one application and one H2 database. Tenant identity comes from the validated JWT, and task and user operations are scoped to that tenant.

## Technology

- Java 25
- Spring Boot 4.2.0
- Spring WebMVC, Spring Security, Spring Data JPA, Jakarta Validation
- File-backed H2 database locally; in-memory H2 on Render
- JJWT 0.12.6 and BCrypt
- JUnit, Spring Boot test starters, JaCoCo

## Run locally

The application requires a JWT signing secret of at least 32 UTF-8 bytes. Generate one for the current PowerShell session and start the application:

```powershell
$env:JWT_SECRET = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
mvn spring-boot:run
```

The Base64 text is used as the secret string. Store a persistent production secret in a secret manager. Do not commit it. By default, the H2 database is stored as `./task_management.mv.db` and survives application restarts. H2 database files are excluded from Git.

### Open the H2 console

While the application is running locally, open [http://localhost:8080/h2-console](http://localhost:8080/h2-console). The H2 form may default to `jdbc:h2:~/test`; replace that value with `jdbc:h2:file:./task_management;DB_CLOSE_ON_EXIT=FALSE`. Use `DB_USERNAME` and `DB_PASSWORD` from your environment (defaults are H2's `sa` user and an empty password). The console is enabled for local development and disabled in the Render container.

### Deploy to Render

The multi-stage `Dockerfile` builds the Spring Boot JAR, then packages it in a Java 25 runtime image. The container starts with the `render` Spring profile, which uses in-memory H2 and disables the H2 console. Local development continues to use file-backed H2.

To deploy, push this project to a GitHub repository, then in Render choose **New → Web Service**, connect that repository, and select **Docker** as the runtime. Render builds the JAR as part of the Docker image. Set `JWT_SECRET` to a private random value of at least 32 bytes; Render supplies `PORT`. The in-memory database and disabled console are selected by the Docker image's `render` profile.

On Render's free plan, the service spins down after 15 minutes without inbound traffic, and the next request can take about a minute to start it. The in-memory H2 database resets whenever the service restarts or spins down, and also on redeploy. This is expected for this setup. See [Render free service limits](https://render.com/docs/free) and [Render's Docker deployment guidance](https://render.com/docs/docker).

Run the tests and coverage check:

```powershell
mvn test
mvn verify
```

JaCoCo writes its HTML report to `target/site/jacoco/index.html`. `mvn verify` fails if line coverage drops below 80%.

## Package structure

```text
com.techpulse.taskmanagement
├── config       Security and authentication configuration
├── controller   HTTP endpoints
├── dto          Validated request and safe response records
├── entity       Tenant, User, and Task persistence entities
├── enums        Role and TaskStatus
├── exception    API errors and centralized response handling
├── repository   Selected persistence operations
├── security     JWT filter/service, authenticated principal, tenant context
└── service      Registration, authentication, users, and task rules
```

## Database design

The application uses a single H2 database. `tenants` is the root organization table. `app_users` and `tasks` each contain a required `tenant_id` foreign key and tenant lookup indexes. User emails are globally unique so login by email identifies exactly one account before a tenant is known.

Tasks reference their tenant and optional assignee. A composite foreign key from `(assigned_to, tenant_id)` to `(app_users.id, app_users.tenant_id)` prevents a task from being assigned to a user in another tenant at the database layer. A missing assignee is allowed when a manager first creates a task.

The tenant root table has no `tenant_id` column because it represents the tenants themselves. Every tenant-owned table has one.

## API

All endpoints except tenant registration and login require a bearer token. Request DTOs reject unknown JSON fields, including any attempt to send `tenant_id`.

| Method and path | Access | Behavior |
| --- | --- | --- |
| `POST /tenant/register` | Public | Creates a tenant and its first ADMIN atomically |
| `POST /auth/login` | Public | Verifies email/password and returns a JWT |
| `POST /users` | ADMIN | Creates a user under the caller's tenant |
| `GET /users` | ADMIN, MANAGER | Lists tenant users; MANAGER results omit MANAGER accounts |
| `GET /users/{id}` | ADMIN, MANAGER | Views one visible user in the caller's tenant |
| `PATCH /users/{id}` | ADMIN | Updates a user's email, password, and/or role |
| `DELETE /users/{id}` | ADMIN | Deletes a user when no task is assigned to them |
| `POST /tasks` | MANAGER | Creates a tenant task in `TODO` status |
| `PUT /tasks/{id}/assign` | MANAGER | Assigns a same-tenant user to a same-tenant task |
| `PUT /tasks/{id}/status` | USER | Updates the status of a task assigned to that user |
| `GET /tasks` | ADMIN, MANAGER, USER | Lists tenant tasks; USER sees only their assignments |

### Register a tenant

```http
POST /tenant/register
Content-Type: application/json

{
  "tenantName": "Northwind",
  "admin": {
    "email": "admin@northwind.example",
    "password": "Use-a-long-password-123"
  }
}
```

The response returns the tenant and initial admin IDs, name, creation time, and email. It never returns the password hash.

### Login

```http
POST /auth/login
Content-Type: application/json

{
  "email": "admin@northwind.example",
  "password": "Use-a-long-password-123"
}
```

Response:

```json
{
  "tokenType": "Bearer",
  "accessToken": "<signed-jwt>",
  "expiresInSeconds": 86400
}
```

Pass the returned token as `Authorization: Bearer <signed-jwt>` on protected requests.

### Create a user

```http
POST /users
Authorization: Bearer <signed-jwt>
Content-Type: application/json

{
  "email": "manager@northwind.example",
  "password": "Another-long-password-123",
  "role": "MANAGER"
}
```

Roles accepted are `ADMIN`, `MANAGER`, and `USER`. Only an authenticated ADMIN can create users.

### View and manage users

```http
GET /users
Authorization: Bearer <admin-or-manager-jwt>
```

ADMIN sees all users in the tenant. MANAGER sees tenant users except MANAGER accounts. `GET /users/{id}` applies the same visibility rule and returns not-found for a different tenant's user or a MANAGER account hidden from a MANAGER.

Only ADMIN can edit or delete a user. Updates are partial; send one or more fields:

```http
PATCH /users/7
Authorization: Bearer <admin-jwt>
Content-Type: application/json

{
  "email": "new-address@northwind.example",
  "role": "USER"
}
```

To reset a password, include `password` in the patch request. Passwords remain BCrypt-hashed and are never returned. The tenant must retain at least one ADMIN, and a user with assigned tasks must have those tasks reassigned before deletion.

### Create and assign a task

```http
POST /tasks
Authorization: Bearer <manager-jwt>
Content-Type: application/json

{
  "title": "Prepare launch checklist",
  "description": "Review the remaining release steps."
}
```

```http
PUT /tasks/1/assign
Authorization: Bearer <manager-jwt>
Content-Type: application/json

{
  "userId": 7
}
```

Task creation sets `tenant_id` from the authenticated request context. Assignment checks the task and user through tenant-scoped repository methods and is backed by the composite database foreign key.

### Update task status and list tasks

```http
PUT /tasks/1/status
Authorization: Bearer <assigned-user-jwt>
Content-Type: application/json

{
  "status": "IN_PROGRESS"
}
```

Allowed statuses are `TODO`, `IN_PROGRESS`, and `DONE`.

```http
GET /tasks
Authorization: Bearer <signed-jwt>
```

ADMIN and MANAGER receive all tasks in their tenant. USER receives only their assigned tasks.

### Errors

Responses use a consistent JSON shape with a timestamp, HTTP status, error, message, and request path. Invalid input returns `400`, missing or invalid authentication returns `401`, role denial returns `403`, inaccessible or unknown task/user IDs return `404`, and duplicate emails return `409`.

## JWT and tenant isolation

The token is signed with an HMAC key from `JWT_SECRET` and expires after 24 hours, as configured in `application.yaml`. Its payload contains the standard subject, issue time, and expiry, plus these application claims:

```json
{
  "sub": "admin@northwind.example",
  "user_id": 12,
  "tenant_id": 3,
  "role": "ADMIN",
  "iat": 1795280000,
  "exp": 1795366400
}
```

The `OncePerRequestFilter` validates the signature and expiry before creating an authenticated principal. It copies the trusted tenant claim into `TenantContext` for the request and always clears the `ThreadLocal` in a `finally` block. Controllers never accept tenant identity from caller input.

Services enforce role, tenant, and task ownership rules. `UserRepository` and `TaskRepository` intentionally expose only selected methods; they do not inherit unscoped `findAll` or `findById` operations. Tenant-owned lookups include `tenant_id`, and inaccessible cross-tenant IDs return not-found. The database foreign keys add a final ownership constraint for user and task relationships.

Authentication performs a global lookup by normalized email because the tenant is not known until that user is verified. Global email uniqueness makes that lookup unambiguous. Subsequent tenant-owned data access is scoped by the tenant claim.

## Tests

Integration tests cover tenant registration, login, JWT claims, BCrypt password storage, role-based user viewing and management, task creation and assignment, user-specific task lists and status updates, invalid and expired JWTs, unknown tenant request fields, cross-tenant ID guessing, and the composite database constraint.

The assignment also asks for a GitHub upload and a five-minute project walkthrough video; those are separate submission steps after reviewing the code and test report.
