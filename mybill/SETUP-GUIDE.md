# MyBill – Full stack setup guide (new laptop)

This project uses:

- **Backend:** Java 21, Spring Boot 3.3, Maven, MongoDB  
- **Frontend:** Angular 17, Node.js, npm  

Use this guide to install everything and run the app locally (Windows).

---

## 1. Install Java 21 (JDK)

- Download **Oracle JDK 21** or **Eclipse Temurin 21**:  
  - Oracle: https://www.oracle.com/java/technologies/downloads/#java21  
  - Temurin: https://adoptium.net/temurin/releases/?version=21&os=windows  
- Run the installer and use default options.
- **Verify:** Open a new Command Prompt or PowerShell and run:
  ```bash
  java -version
  javac -version
  ```
  You should see version 21.x.

---

## 2. Install Apache Maven

- Download Maven (Binary zip): https://maven.apache.org/download.cgi  
- Unzip to a folder, e.g. `C:\Program Files\Apache\maven`.  
- Add Maven to PATH:
  - **System** → **Advanced system settings** → **Environment Variables**
  - Under **System variables**, select **Path** → **Edit** → **New**
  - Add: `C:\Program Files\Apache\maven\bin` (adjust path if different)
- **Verify:** In a new terminal:
  ```bash
  mvn -version
  ```

---

## 3. Install Node.js (LTS)

- Download **Node.js LTS** (e.g. 20.x): https://nodejs.org/  
- Run the installer; ensure **“Add to PATH”** is checked.
- **Verify:** In a new terminal:
  ```bash
  node -version
  npm -version
  ```
  Use Node 18+ (20 LTS recommended for Angular 17).

---

## 4. Install MongoDB

**Option A – MongoDB Community (local)**

- Download: https://www.mongodb.com/try/download/community  
- Run installer; choose **Complete**.  
- Optional: install **MongoDB Compass** (GUI).  
- Start MongoDB service:
  - **Services** (Win + R → `services.msc`) → find **MongoDB** → Start, set Startup type **Automatic**  
  - Or from terminal (if `mongod` is in PATH):
    ```bash
    mongod --dbpath C:\data\db
    ```
    (Create `C:\data\db` if needed.)

**Option B – MongoDB Atlas (cloud)**

- Sign up: https://www.mongodb.com/cloud/atlas  
- Create a free cluster and get the **connection string**.  
- Later you’ll put this in `application-dev.properties` (see step 7).

**Create user (local MongoDB):**

- Open MongoDB Shell or Compass.  
- Create DB user (e.g. for `billing_db`):
  ```javascript
  use admin
  db.createUser({
    user: "fxstar",
    pwd: "fxstar",
    roles: [ { role: "userAdminAnyDatabase", db: "admin" }, "readWriteAnyDatabase" ]
  })
  ```
- Default dev config expects: `mongodb://fxstar:fxstar@localhost:27017/billing_db?authSource=admin`

---

## 5. Get the project code

- If you use Git:
  ```bash
  git clone <your-repo-url> mybill
  cd mybill
  ```
- Or copy the project folder (e.g. `d:\mybill`) to your laptop.

---

## 6. Backend setup and run

- Open terminal in project root (e.g. `d:\mybill`).
  ```bash
  cd billing-backend
  mvn clean install
  ```
- Run with **dev** profile (uses `application-dev.properties`):
  ```bash
  mvn spring-boot:run -Dspring-boot.run.profiles=dev
  ```
- Backend will be at: **http://localhost:1104/api**  
- Swagger (dev): http://localhost:1104/api/swagger-ui.html  

---

## 7. Frontend setup and run

- In a **new** terminal, from project root:
  ```bash
  cd frontend
  npm install
  npm start
  ```
- Frontend will be at: **http://localhost:4200**  
- It is configured to call the API at `http://localhost:1104/api` (see `frontend/src/environments/environment.ts`).

---

## 8. Configuration (optional)

**MongoDB (if not using default dev settings):**

- Edit: `billing-backend/src/main/resources/application-dev.properties`
- Change URI if needed, e.g. for Atlas:
  ```properties
  spring.data.mongodb.uri=mongodb+srv://USER:PASSWORD@cluster0.xxxxx.mongodb.net/billing_db
  ```

**Backend port / context:**

- Default: port **1104**, context **/api**.  
- If you change port, update `frontend/src/environments/environment.ts`:
  ```ts
  apiUrl: 'http://localhost:YOUR_PORT/api'
  ```

**Default login (dev):**

- Username: `admin`  
- Password: `admin123`  
- (Defined in `application-dev.properties`.)

---

## 9. Quick checklist

| Step              | Command / Action                                      |
|-------------------|--------------------------------------------------------|
| Java 21           | `java -version` → 21.x                                |
| Maven             | `mvn -version`                                        |
| Node.js           | `node -version`, `npm -version`                       |
| MongoDB           | Service running or `mongod` / Atlas URI set           |
| Backend           | `cd billing-backend` → `mvn spring-boot:run -Dspring-boot.run.profiles=dev` |
| Frontend          | `cd frontend` → `npm install` → `npm start`           |
| Open app          | Browser: http://localhost:4200                        |
| API docs          | http://localhost:1104/api/swagger-ui.html             |

---

## 10. Troubleshooting

- **“java not found”**  
  Install JDK 21 and add it to PATH; restart the terminal.

- **“mvn not found”**  
  Add Maven `bin` folder to system PATH.

- **“Cannot connect to MongoDB”**  
  Start MongoDB service (or `mongod`), or fix URI in `application-dev.properties`. For Atlas, use the full connection string and ensure IP is whitelisted.

- **Frontend “Cannot GET /api/…”**  
  Backend must be running on port 1104; `apiUrl` in `environment.ts` must be `http://localhost:1104/api`.

- **Port 1104 or 4200 in use**  
  Change backend port in `application-dev.properties` and `environment.ts`, or frontend port: `ng serve --port 4300`.

---

## Stack summary

| Component   | Version / tech      | Purpose                    |
|------------|----------------------|----------------------------|
| JDK        | 21                   | Backend runtime            |
| Maven      | 3.9+                 | Backend build              |
| Spring Boot| 3.3.0                | Backend framework          |
| MongoDB    | 6+ / Atlas           | Database                   |
| Node.js    | 18+ (20 LTS)         | Frontend tooling           |
| npm        | 9+                   | Frontend packages          |
| Angular    | 17                   | Frontend framework         |
| Angular CLI| 17                   | Frontend build/serve       |
