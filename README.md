# PayFlow

> A UPI-style payment app — the REST API that powers it.

## Brief

### 1. The Scenario

Every time you tap **Pay** on PhonePe or Google Pay, a backend server receives that request, validates the sender, records the transaction, and updates balances — all in under a second.

PayFlow is a simplified version of that system. You are building the REST API that powers it: registering users, giving them a wallet balance, and recording money transfers between them.

There is no UI. No frontend. Just a clean, database-backed API that a frontend team could plug into tomorrow — which is exactly how real fintech backends are built and handed off.

By the end of this assignment, the following will work entirely through HTTP calls from your terminal:

- **Register a user** — `POST` a new user with a name, UPI ID, and opening balance.
- **Look up a user** — `GET` a user by their ID, or search by their UPI ID.
- **List all users** — `GET` every registered user in the system.
- **Send money** — `POST` a transaction from one user to another. The amount and sender/receiver get persisted.

### 2. Your Entities

PayFlow has one core entity for this assignment: the **User**. Keep it simple — relationships (like linking transactions to users via foreign keys) are a topic for the next session.

#### The User Entity

![User entity](docs/images/user-entity.png)

> Notice that camelCase Java field names (`upiId`, `phoneNumber`) automatically become snake_case column names (`upi_id`, `phone_number`) in the database — this is the JPA naming convention demonstrated in class.

#### The Transaction Entity

![Transaction entity](docs/images/transaction-entity.png)

A `Transaction` records a single money transfer. It is a second entity — build it as a plain `@Entity` with the fields below. You do not need to model the relationship between `Transaction` and `User` with foreign keys yet (that comes in a future session). Simply store the sender and receiver UPI IDs as plain strings for now.

## 3. Tasks

### Task 1 — Project Setup (10 marks)

- Create a Spring Boot project via [Spring Initializr](https://start.spring.io/) with: **Spring Web**, **Spring Data JPA**, **H2 Database**.
- Organise into four packages: `entity`, `repository`, `service`, `controller`.
- Write a `README.md` covering: how to run the app, what each layer does, and how Spring Boot's three features (embedded server, auto-configuration, production-ready defaults) appear in this PayFlow project specifically.

### Task 2 — Entities & Database (15 marks)

- Create both the `User` and `Transaction` entity classes with the fields above.
- Annotate each with `@Entity`, `@Id`, `@GeneratedValue`. No hand-written SQL — Spring Data JPA creates both tables automatically.
- Add `spring.jpa.show-sql=true` to `application.properties`. On first startup, paste the two `create table (...)` statements from the console into your write-up.
- Open `/h2-console` and screenshot both tables (`SELECT * FROM USER` and `SELECT * FROM TRANSACTION`) showing correct columns before any data is inserted.

### Task 3 — Repository Layer (10 marks)

- Create `UserRepository` extending `JpaRepository<User, Long>` and `TransactionRepository` extending `JpaRepository<Transaction, Long>`.
- In `UserRepository`, add the derived query method `findByUpiId(String upiId)`. This is how your app will look up a user before sending money.
- In your README, paste the SQL JPA generates for `findByUpiId` and explain:
  - (a) how JPA derives it from the method name, and
  - (b) what the `?` placeholder means.

### Task 4 — Service Layer (15 marks)

- Create `UserService` (`@Service`) with methods: `registerUser(User user)`, `getAllUsers()`, `getUserById(Long id)`, `findByUpiId(String upiId)`.
- Create `TransactionService` (`@Service`) with method: `sendMoney(Transaction transaction)`. For now this just saves the transaction record — balance deduction logic is not required.
- In both services, inject the respective repository using `@Autowired`. Add a comment in the code explaining what Spring is doing at startup to make this work.

### Task 5 — Controller & REST Endpoints (30 marks)

- Create `UserController` (`@RestController`, base path `/users`) and `TransactionController` (`@RestController`, base path `/transactions`).
- Implement the following endpoints:

![Endpoints](docs/images/endpoints.png)

- Test all four endpoints using `curl` from your terminal. Include the curl commands and output for each. After registering two users and sending money between them, screenshot `SELECT * FROM USER` and `SELECT * FROM TRANSACTION` in the H2 console.
- Demonstrate `@RequestBody`: call `POST /users` once with `@RequestBody` and once without. Show the difference in what the `User` object looks like inside the controller (debugger or `println`). Explain in one paragraph why the fields are `null` without it.

### Task 6 — Custom Query (10 marks)

- Wire `findByUpiId` into the service and controller, and add a `GET /users/upi/{upiId}` endpoint that returns the user matching that UPI ID.
- Add a second method to `UserRepository` using the `@Query` annotation with a JPQL query (not native SQL) — for example, find all users whose balance is above a given amount.
- In your README, compare the three approaches to custom queries (derived method names, `@Query` JPQL, and native SQL) and explain why native queries are the least preferred.

## 4. Conceptual Write-Up (10 marks)

Answer all six questions in your own words, 3–5 sentences each. Every question maps to something covered in class.

1. **Request lifecycle** — Trace what happens from the moment `curl` sends `POST /users` to the moment your `createUser` method runs. Name the Dispatcher Servlet and Handler Adapter in your answer.
2. **Serialisation** — When you POST a JSON payload like `{"name":"Priya","upiId":"priya@okaxis"}`, what converts it into a Java `User` object? What happens if the JSON key is `"upi_id"` instead of `"upiId"`?
3. **Spring Boot features** — Name the three Spring Boot features. For each one, point to a specific thing in your PayFlow project where that feature is doing work for you.
4. **Spring vs. Spring Boot** — If you had used plain Spring instead of Spring Boot for PayFlow, what would you have had to set up manually? What does Spring Boot take care of automatically?
5. **Stateless REST** — Your `POST /transactions` endpoint does not remember anything about the previous request. What does stateless mean, and why does it matter if PayFlow eventually runs on three servers behind a load balancer?
6. **Persistence** — You stored transactions in the H2 database, not in a Java `List`. What would have happened to all the transaction records if you had used a `List` and then restarted the server? Why is this unacceptable for a payments app?

## Submission Guidelines

- Push the complete Spring Boot project to a **public GitHub repo**, named `payflow-api` or similar.
- Place your write-up (answers + screenshots) as a PDF or Word doc in the repo root.
- Make sure the app starts cleanly from a fresh run before submitting.

## Deliverables

- GitHub link
