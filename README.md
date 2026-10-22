# Agriva Backend

## Multi-Tenant Modular Monolith Architecture

Agriva is a multi-tenant agricultural management platform built with **Java 21**, **Spring Boot**, **Spring Security**, **Spring Data JPA**, **PostgreSQL**, **Redis**, **Kafka**, **Flyway**, and **Spring Modulith**.

The backend is designed as a **modular monolith**.

Instead of deploying `user-service`, `tenant-service`, `farm-service`, `income-service`, and `expense-service` as separate microservices, Agriva contains these as isolated business modules inside one Spring Boot application.

---

# 1. Architecture Overview

```text
                           AGRIVA BACKEND
                                  |
                         Spring Boot Application
                                  |
                     +------------+------------+
                     |                         |
              Infrastructure             Business Modules
                     |                         |
          +----------+----------+       +------+-------+
          |          |          |       |      |       |
       Security    Config     Common   Auth   Tenant   User
                                           |
                         +-----------------+----------------+
                         |                 |                |
                        Farm            Finance         Inventory
                         |                 |                |
                    Agriculture     +------+-------+       |
                         |          |              |       |
                     Production   Income         Expense   |
                         |          |              |       |
                         +----------+--------------+-------+
                                    |
                               Sales / CRM
                                    |
                         +----------+----------+
                         |                     |
                      Customer              Supplier
                                    |
                              Reporting
                                    |
                                Audit


All modules run inside the same JVM and are packaged into one executable JAR.

---

# 2. Why a Modular Monolith?

Agriva uses a modular monolith rather than immediately implementing a microservice architecture.

The benefits are:

* One application to deploy.
* One executable JAR.
* One Maven build.
* One primary database.
* Easier local development.
* Easier debugging.
* Lower infrastructure complexity.
* Clear business boundaries.
* Ability to introduce asynchronous events.
* Ability to extract individual modules into microservices later if required.

The application can therefore start simple while maintaining architectural boundaries for future growth.

---

# 3. Important Terminology

The following are **business modules**, not independent microservices:

```text
auth
tenant
user
farm
finance
inventory
sales
customer
supplier
notification
reporting
audit
```

For example:

```text
com.agriva.user
```

is a Java package.

It is not a separate Maven module.

Likewise:

```text
com.agriva.tenant
```

is a Java package.

---

# 4. Maven Architecture

Agriva uses:

```text
ONE Maven project
ONE pom.xml
ONE Spring Boot application
ONE executable JAR
```

The project does **not** use:

```text
auth-service/pom.xml
user-service/pom.xml
tenant-service/pom.xml
farm-service/pom.xml
```

Instead:

```text
Agriva-backend/
│
├── pom.xml
│
└── src/
    └── main/
        └── java/
            └── com/
                └── agriva/
```

---

# 5. Project Structure

```text
Agriva-Backend/
│
├── .mvn/
├── .gitignore
├── mvnw
├── mvnw.cmd
│
├── Dockerfile
├── docker-compose.yaml
├── .env.example
│
├── pom.xml
├── README.md
│
└── src/
    │
    ├── main/
    │   │
    │   ├── java/
    │   │   └── com/
    │   │       └── akentech/
    │   │           └── Agriva/
    │   │
    │   │               ├── AgrivaApplication.java
    │   │               │
    │   │               ├── config/
    │   │               │   ├── SecurityConfig.java
    │   │               │   ├── PasswordConfig.java
    │   │               │   ├── JacksonConfig.java
    │   │               │   ├── CorsConfig.java
    │   │               │   └── OpenApiConfig.java
    │   │               │
    │   │               ├── security/
    │   │               │   ├── JwtService.java
    │   │               │   ├── JwtAuthenticationFilter.java
    │   │               │   ├── TenantContext.java
    │   │               │   ├── TenantContextHolder.java
    │   │               │   ├── CustomUserDetailsService.java
    │   │               │   ├── AuthenticatedUser.java
    │   │               │   └── SecurityConstants.java
    │   │               │
    │   │               ├── auth/
    │   │               │   ├── controller/
    │   │               │   │   └── AuthController.java
    │   │               │   ├── service/
    │   │               │   │   ├── AuthService.java
    │   │               │   │   └── AuthServiceImpl.java
    │   │               │   ├── dto/
    │   │               │   │   ├── LoginRequest.java
    │   │               │   │   ├── RegisterTenantRequest.java
    │   │               │   │   ├── RegisterSubUserRequest.java
    │   │               │   │   ├── AuthResponse.java
    │   │               │   │   └── TokenValidationResponse.java
    │   │               │   └── exception/
    │   │               │       ├── InvalidCredentialsException.java
    │   │               │       ├── TenantNotFoundException.java
    │   │               │       └── RegistrationException.java
    │   │               │
    │   │               ├── tenant/
    │   │               │   ├── controller/
    │   │               │   ├── service/
    │   │               │   ├── repository/
    │   │               │   ├── entity/
    │   │               │   └── dto/
    │   │               │
    │   │               ├── user/
    │   │               │   ├── controller/
    │   │               │   ├── service/
    │   │               │   ├── repository/
    │   │               │   ├── entity/
    │   │               │   └── dto/
    │   │               │
    │   │               ├── role/
    │   │               │   ├── controller/
    │   │               │   ├── service/
    │   │               │   ├── repository/
    │   │               │   ├── entity/
    │   │               │   └── dto/
    │   │               │
    │   │               ├── farm/
    │   │               │   ├── controller/
    │   │               │   ├── service/
    │   │               │   ├── repository/
    │   │               │   ├── entity/
    │   │               │   └── dto/
    │   │               │
    │   │               ├── income/
    │   │               │   ├── controller/
    │   │               │   ├── service/
    │   │               │   ├── repository/
    │   │               │   ├── entity/
    │   │               │   └── dto/
    │   │               │
    │   │               ├── expense/
    │   │               │   ├── controller/
    │   │               │   ├── service/
    │   │               │   ├── repository/
    │   │               │   ├── entity/
    │   │               │   └── dto/
    │   │               │
    │   │               ├── inventory/
    │   │               ├── sales/
    │   │               ├── customer/
    │   │               ├── supplier/
    │   │               ├── notification/
    │   │               ├── reporting/
    │   │               ├── subscription/
    │   │               │
    │   │               └── common/
    │   │                   ├── exception/
    │   │                   ├── response/
    │   │                   ├── audit/
    │   │                   └── util/
    │   │
    │   └── resources/
    │       ├── application.properties
    │       ├── application-dev.properties
    │       ├── application-docker.properties
    │       ├── application-prod.properties
    │       │
    │       └── db/
    │           └── migration/
    │               ├── V1__create_tenant_table.sql
    │               ├── V2__create_user_table.sql
    │               ├── V3__create_roles_tables.sql
    │               └── ...
    │
    └── test/
        └── java/
            └── com/
                └── akentech/
                    └── agriva/
```

---

# 6. Module Structure

Each business module follows a consistent structure.

For example, the User module:

```text
user/
│
├── controller/
│   └── UserController.java
│
├── service/
│   ├── UserService.java
│   └── UserServiceImpl.java
│
├── repository/
│   └── UserRepository.java
│
├── entity/
│   └── User.java
│
├── dto/
│   ├── UserResponse.java
│   ├── UpdateUserRequest.java
│   └── ChangePasswordRequest.java
│
└── exception/
    └── UserAlreadyExistsException.java
```

The same pattern can be applied to other modules.

---

# 7. User Module

Package:

```text
com.agriva.user
```

Responsibilities:

* User registration data.
* User profile management.
* User status.
* Password changes.
* User lookup.
* User-to-tenant relationships.
* User roles.

Example:

```text
user/
├── controller/
├── service/
├── repository/
├── entity/
├── dto/
└── exception/
```

The User module owns its own entities and repositories.

Other modules should not directly manipulate `UserRepository`.

---

# 8. Tenant Module

Package:

```text
com.agriva.tenant
```

Responsibilities:

* Tenant creation.
* Tenant configuration.
* Tenant status.
* Tenant information.
* Tenant isolation.
* Tenant lifecycle.

Structure:

```text
tenant/
├── controller/
├── service/
├── repository/
├── entity/
├── dto/
└── exception/
```

The tenant is the primary isolation boundary of the application.

---

# 9. Authentication Module

Package:

```text
com.agriva.auth
```

Responsibilities:

* Registration.
* Login.
* Logout.
* JWT generation.
* Refresh tokens.
* Token validation.
* Authentication events.

Authentication should work together with:

```text
com.agriva.security
```

The authentication module handles authentication operations while the security package provides the underlying security infrastructure.

---

# 10. Multi-Tenancy

Agriva uses tenant-based data isolation.

Conceptually:

```text
Tenant
   |
   +-- Users
   |
   +-- Farms
   |
   +-- Crops
   |
   +-- Livestock
   |
   +-- Production
   |
   +-- Inventory
   |
   +-- Sales
   |
   +-- Customers
   |
   +-- Suppliers
   |
   +-- Income
   |
   +-- Expenses
   |
   +-- Reports
```

Most business entities contain:

```text
tenant_id
```

This prevents data belonging to one tenant from being returned to another tenant.

---

# 11. Authentication Flow

```text
Client
   |
   | POST /api/v1/auth/login
   v
AuthController
   |
   v
AuthService
   |
   v
UserRepository
   |
   v
Password Verification
   |
   v
JwtService
   |
   v
JWT
```

The JWT contains tenant information.

Example:

```json
{
  "sub": "user-id",
  "tenantId": "tenant-id",
  "email": "owner@example.com",
  "roles": [
    "OWNER"
  ]
}
```

For authenticated requests:

```text
HTTP Request
     |
     v
JwtAuthenticationFilter
     |
     v
Validate JWT
     |
     v
Extract userId
     |
     v
Extract tenantId
     |
     v
TenantContextHolder
     |
     v
Controller
     |
     v
Service
     |
     v
Repository
```

---

# 12. Finance Module

Finance should be grouped under:

```text
com.agriva.finance
```

with:

```text
finance/
│
├── income/
│   ├── controller/
│   ├── service/
│   ├── repository/
│   ├── entity/
│   └── dto/
│
└── expense/
    ├── controller/
    ├── service/
    ├── repository/
    ├── entity/
    └── dto/
```

Therefore:

```text
IncomeService.java
```

belongs to:

```text
com.agriva.finance.income.service
```

and:

```text
ExpenseService.java
```

belongs to:

```text
com.agriva.finance.expense.service
```

This is preferable to creating independent:

```text
income-service
expense-service
```

directories.

---

# 13. Database

K-Farms uses PostgreSQL.

The initial architecture uses:

```text
One PostgreSQL database
One schema
Multiple tenant-aware tables
```

Example:

```text
agriva
│
├── tenants
├── users
├── roles
├── permissions
├── farms
├── farm_members
├── crops
├── livestock
├── production
├── inventory_items
├── customers
├── suppliers
├── sales
├── income
├── expenses
├── notifications
└── audit_logs
```

Business tables should contain a tenant identifier where appropriate:

```sql
tenant_id UUID NOT NULL
```

---

# 14. Database Migrations

Flyway migrations are stored under:

```text
src/main/resources/db/migration/
```

Example:

```text
db/migration/
├── V1__create_tenants.sql
├── V2__create_users.sql
├── V3__create_roles.sql
├── V4__create_permissions.sql
├── V5__create_user_roles.sql
├── V6__create_farms.sql
├── V7__create_farm_members.sql
├── V8__create_crops.sql
├── V9__create_livestock.sql
├── V10__create_production.sql
├── V11__create_inventory.sql
├── V12__create_customers.sql
├── V13__create_suppliers.sql
├── V14__create_sales.sql
├── V15__create_income.sql
├── V16__create_expenses.sql
└── V17__create_audit_logs.sql
```

Never manually modify an already-applied production migration.

Create a new migration instead.

---

# 15. Redis

Redis is intended for:

* Session management.
* Refresh token storage.
* Temporary authentication data.
* Caching.
* Rate limiting where required.
* Short-lived application data.

Redis should not become the primary source of truth for business data.

PostgreSQL remains the primary database.

---

# 16. Kafka

Kafka is used for asynchronous domain events.

Example:

```text
User Registered
      |
      v
UserRegisteredEvent
      |
      +----> Notification Module
      |
      +----> Audit Module
      |
      +----> Reporting
```

Other examples:

```text
SaleCompletedEvent
ExpenseCreatedEvent
IncomeRecordedEvent
FarmCreatedEvent
UserRegisteredEvent
```

Kafka should be used where asynchronous processing provides a real benefit.

Simple operations should remain synchronous.

---

# 17. Spring Modulith

Spring Modulith provides structural boundaries between the business modules.

The application should conceptually contain modules such as:

```text
auth
tenant
user
farm
finance
inventory
sales
customer
supplier
notification
reporting
audit
```

The goal is to prevent uncontrolled dependencies between modules.

For example:

```text
Sales
  |
  +----> Customer API
  |
  +----> Inventory API
  |
  +----> Finance API
```

rather than allowing Sales to directly access every repository in the application.

---

# 18. API Structure

The REST API should use versioning.

Example:

```text
/api/v1/auth
/api/v1/tenants
/api/v1/users
/api/v1/farms
/api/v1/crops
/api/v1/livestock
/api/v1/production
/api/v1/inventory
/api/v1/sales
/api/v1/customers
/api/v1/suppliers
/api/v1/income
/api/v1/expenses
/api/v1/reports
```

Examples:

```text
POST   /api/v1/auth/register
POST   /api/v1/auth/login
POST   /api/v1/auth/refresh
POST   /api/v1/auth/logout

GET    /api/v1/users/me
PUT    /api/v1/users/me
PUT    /api/v1/users/me/password

GET    /api/v1/tenants/current

GET    /api/v1/farms
POST   /api/v1/farms
GET    /api/v1/farms/{id}
PUT    /api/v1/farms/{id}
DELETE /api/v1/farms/{id}

GET    /api/v1/income
POST   /api/v1/income

GET    /api/v1/expenses
POST   /api/v1/expenses
```

---

# 19. Security Model

Authentication:

```text
JWT
```

Authorization:

```text
Role-Based Access Control
```

Example roles:

```text
SUPER_ADMIN
TENANT_ADMIN
OWNER
MANAGER
FARM_MANAGER
ACCOUNTANT
WORKER
VIEWER
```

Permissions can later be introduced:

```text
FARM_CREATE
FARM_READ
FARM_UPDATE
FARM_DELETE

INCOME_CREATE
INCOME_READ
INCOME_UPDATE
INCOME_DELETE

EXPENSE_CREATE
EXPENSE_READ
EXPENSE_UPDATE
EXPENSE_DELETE
```

---

# 20. Request Isolation

Every authenticated request should resolve the tenant:

```text
JWT
 |
 +-- userId
 |
 +-- tenantId
 |
 +-- roles
```

The tenant ID is placed into:

```text
TenantContextHolder
```

Business services can then use the current tenant.

Example conceptual flow:

```text
TenantContextHolder
        |
        v
Current tenant
        |
        v
Service
        |
        v
Repository
        |
        v
tenant_id filtering
```

The tenant must never be trusted simply because a client sends:

```json
{
  "tenantId": "some-other-tenant"
}
```

The authenticated tenant context should be authoritative.

---

# 21. Docker Architecture

Development infrastructure can be started with:

```bash
docker compose up -d
```

Typical infrastructure:

```text
Docker Compose
│
├── PostgreSQL
├── Redis
└── Kafka
```

The Spring Boot application can either run:

```text
locally from IntelliJ
```

while infrastructure runs in Docker, or the complete application can run inside Docker.

---

# 22. Build

The project is built using Maven.

Clean build:

```bash
./mvnw clean package
```

Windows:

```cmd
mvnw.cmd clean package
```

Skip tests:

```bash
./mvnw clean package -DskipTests
```

Run:

```bash
./mvnw spring-boot:run
```

Run the packaged application:

```bash
java -jar target/kfarm-0.0.1-SNAPSHOT.jar
```

The exact JAR name depends on the Maven `artifactId` and version.

---

# 23. IntelliJ Development

The project should be opened as a single Maven project.

Do not create:

```text
User Service Module
Tenant Service Module
Income Service Module
Expense Service Module
```

Instead, create Java packages:

```text
com.agriva.user
com.agriva.tenant
com.agriva.finance.income
com.agriva.finance.expense
```

For example:

```text
New
 |
 +-- Package
      |
      +-- com.agriva.user
```

Then:

```text
com.agriva.user
    |
    +-- controller
    +-- service
    +-- repository
    +-- entity
    +-- dto
```

---

# 24. Testing

Tests should mirror the application modules.

Example:

```text
src/test/java/com/agriva/

├── auth/
├── tenant/
├── user/
├── farm/
├── finance/
│   ├── income/
│   └── expense/
├── inventory/
├── sales/
└── reporting/
```

Testing levels include:

```text
Unit Tests
Integration Tests
Repository Tests
Controller Tests
Security Tests
Multi-Tenant Isolation Tests
```

Multi-tenant isolation testing is particularly important.

Example:

```text
Tenant A
   |
   +-- Farm A

Tenant B
   |
   +-- Farm B
```

A request authenticated as Tenant A must never be able to retrieve Farm B.

---

# 25. Architectural Rules

The following rules should be maintained.

### Rule 1 — One application

A-Farms is one Spring Boot application.

### Rule 2 — One Maven project

There is one primary `pom.xml`.

### Rule 3 — Business modules are packages

Use:

```text
com.agriva.user
com.agriva.tenant
com.agriva.farm
```

not separate Maven/IntelliJ modules.

### Rule 4 — Modules own their repositories

The User module owns:

```text
UserRepository
```

The Farm module owns:

```text
FarmRepository
```

The Finance module owns:

```text
IncomeRepository
ExpenseRepository
```

### Rule 5 — Avoid direct repository access across modules

Use services, facades, application APIs, or domain events.

### Rule 6 — Tenant isolation is mandatory

Business data must be associated with the correct tenant.

### Rule 7 — Controllers should remain thin

Controllers should handle:

```text
HTTP
DTO validation
request/response mapping
```

Business logic belongs in services/domain components.

### Rule 8 — Services contain business logic

Services coordinate repositories, validation, domain operations, and events.

### Rule 9 — DTOs cross API boundaries

Avoid exposing JPA entities directly from REST controllers.

### Rule 10 — Database changes use Flyway

Do not manually modify production database schemas.

---

# 26. Future Microservice Extraction

One major benefit of this architecture is that individual modules can eventually be extracted.

For example:

```text
Current:

Ariva
 |
 +-- finance
 +-- inventory
 +-- sales
 +-- farm
```

Future:

```text
Agriva Platform
       |
       +-- Core Monolith
       |
       +-- Finance Service
       |
       +-- Inventory Service
       |
       +-- Notification Service
```

This should only happen when there is a real operational reason.

Do not introduce distributed systems complexity before it is necessary.

---

# 27. Recommended Development Order

Implement the system in this order:

```text
1. Project / Maven structure
        |
2. PostgreSQL + Flyway
        |
3. Tenant module
        |
4. User module
        |
5. Role / Permission module
        |
6. Authentication / JWT
        |
7. Multi-tenant security
        |
8. Farm module
        |
9. Agriculture modules
        |
10. Inventory
        |
11. Customers / Suppliers
        |
12. Sales
        |
13. Income / Expenses
        |
14. Reporting
        |
15. Notifications
        |
16. Audit
        |
17. Kafka events
        |
18. Docker production setup
        |
19. Integration tests
```

---

# 28. Final Architecture

The final A-Farms backend should conceptually be:

```text
                       Agriva
                          |
                 Spring Boot  Application
                          |
                 Modular Monolith
                          |
       +------------------+------------------+
       |                  |                  |
   Authentication      Tenant             User
       |                  |                  |
       +------------------+------------------+
                          |
                    Farm Management
                          |
             +------------+------------+
             |            |            |
          Crops       Livestock    Production
             |
             +-------------------------+
             |            |            |
         Inventory      Sales       Customers
                          |
                       Finance
                          |
                   +------+------+
                   |             |
                 Income       Expense
                   |
              +----+----+
              |         |
          Reporting    Audit
                          |
             +------------+------------+
             |                         |
         PostgreSQL                   Redis
             |
           Kafka
```

Deployment:

```text
                    Docker / Server
                          |
                  Agriva Application
                          |
              +-----------+-----------+
              |           |           |
          PostgreSQL    Redis       Kafka
```

The result is **one deployable application with strongly separated business modules**, rather than a collection of independent microservices.