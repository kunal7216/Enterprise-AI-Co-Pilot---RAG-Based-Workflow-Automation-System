

# 🚀 Enterprise AI Co-Pilot (Advanced Version)

## 🎯 FULLY PRODUCTION-READY AI + RAG + FRAUD DETECTION SYSTEM

This is a **complete enterprise-grade Spring Boot application** featuring:

* 🤖 **Ollama AI (FREE, Local LLM)**
* 🧠 **RAG (Retrieval-Augmented Generation) using pgvector**
* 🔍 **Fraud Detection Engine**
* 📄 **PDF / DOCX / XLSX / TXT extraction**
* ⚡ **Explainable AI decisions**
* 🔐 **JWT Authentication**
* 🐳 **Dockerized Architecture**
* 🗄️ **PostgreSQL + pgvector**
* 📊 **Dashboard + Insights APIs**

---

# ⚡ QUICK START (5 MINUTES)

## ✅ Prerequisites

* Docker + Docker Compose
* Java 17+
* Maven

---

## 🚀 Step 1: Build Project

```bash
mvn clean install
```

---

## 🚀 Step 2: Start Full System

```bash
docker compose down -v
docker compose up --build -d
```

---

## 🚀 Step 3: Setup Database (ONE TIME)

```bash
docker exec -it postgres_db psql -U postgres -d enterprise_copilot -c "CREATE EXTENSION IF NOT EXISTS vector;"
docker exec -it postgres_db psql -U postgres -d enterprise_copilot -c "ALTER TABLE workflows ADD COLUMN IF NOT EXISTS embedding vector(384);"
docker exec -it postgres_db psql -U postgres -d enterprise_copilot -c "ALTER TABLE workflows ADD COLUMN IF NOT EXISTS decision_reason TEXT;"
```

---

## 🚀 Step 4: Setup AI Models

```bash
docker exec -it enterprise-ollama ollama pull llama3
docker exec -it enterprise-ollama ollama pull nomic-embed-text
```

---

## 🚀 Step 5: Open Application

* Swagger UI 👉 [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
* Health Check 👉 [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)

---

# 🧠 SYSTEM ARCHITECTURE

```
User Upload
   ↓
Text Extraction (PDF / DOCX / XLSX)
   ↓
Ollama AI (Field Extraction)
   ↓
Fraud Detection Engine
   ↓
Decision Engine (Rules + AI)
   ↓
Embedding Generation
   ↓
pgvector Storage
   ↓
RAG Context Retrieval
   ↓
Final Response (Explainable AI)
```

---

# 🔥 CORE FEATURES

## 1. AI Document Processing

* Extract invoice data
* Confidence scoring
* AI recommendation (APPROVE / ESCALATE / REJECT)

---

## 2. Fraud Detection Engine

* Duplicate detection
* Risk scoring (LOW / MEDIUM / HIGH)
* Rule-based anomaly detection

---

## 3. RAG (VERY ADVANCED)

* Stores embeddings using pgvector
* Retrieves similar past workflows
* Improves AI decision quality

---

## 4. Explainable AI

Each workflow stores:

* `decisionReason` → rule-based reason
* `aiRecommendation` → AI explanation
* `reviewComments` → fraud + audit details

---

## 5. Decision Engine

```text
LOW confidence → ESCALATE
HIGH fraud risk → ESCALATE
Large amount → ESCALATE
Unknown vendor → ESCALATE
Clean + high confidence → AUTO APPROVE
```

---

# 📊 API ENDPOINTS

## 🔓 Public

```
POST /api/auth/register
POST /api/auth/login
```

## 🔐 Protected

```
POST /api/v1/workflows/upload
GET  /api/v1/workflows
GET  /api/v1/workflows/{id}
POST /api/v1/workflows/approve
GET  /api/v1/workflows/stats
GET  /api/v1/workflows/{id}/history
GET  /api/v1/workflows/{id}/rag-insights
GET  /api/v1/workflows/dashboard/insights
```

---

# 🧪 TEST FLOW (IMPORTANT)

### 1. Register User

```json
{
  "username": "admin",
  "email": "admin@test.com",
  "password": "admin123",
  "fullName": "Admin User"
}
```

---

### 2. Login → Get JWT

---

### 3. Upload File

Use:

```
POST /api/v1/workflows/upload
```

Upload:

* PDF
* DOCX
* XLSX
* TXT

---

### 4. Expected Output

```json
{
  "confidenceScore": 0.87,
  "decisionType": "AUTO_APPROVED",
  "decisionReason": "Auto-approved: amount < threshold",
  "aiRecommendation": "Invoice looks valid and low risk",
  "ragInsights": [...]
}
```

---

# 🐳 DOCKER ARCHITECTURE

```
enterprise-app        → Spring Boot Backend
postgres_db           → PostgreSQL + pgvector
enterprise-ollama     → Local AI (LLM)
```

---

# ⚙️ CONFIGURATION

## Database

```properties
SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/enterprise_copilot
```

---

## Ollama

```properties
OLLAMA_BASE_URL=http://ollama:11434
OLLAMA_MODEL=llama3
OLLAMA_EMBEDDING_MODEL=nomic-embed-text
```

---

# 📁 PROJECT STRUCTURE

```
com.enterprise.copilot
│
├── controller
├── service
│   ├── WorkflowService
│   ├── OllamaService
│   ├── FraudDetectionService
│
├── repository
├── entity
├── dto
├── security
├── config
```

---

# 🚀 WHY THIS PROJECT IS POWERFUL

This is NOT a basic CRUD project.

It includes:

* ✅ AI + LLM integration
* ✅ RAG (used in ChatGPT, Google Gemini)
* ✅ Fraud detection system
* ✅ Explainable AI
* ✅ Production architecture
* ✅ Dockerized microservices style

---


# 🏆 FINAL STATUS

| Feature          | Status |
| ---------------- | ------ |
| AI Extraction    | ✅      |
| RAG (pgvector)   | ✅      |
| Fraud Detection  | ✅      |
| Explainable AI   | ✅      |
| Docker Setup     | ✅      |
| Production Ready | ✅      |

---

# 🎯 FINAL COMMAND

```bash
docker compose up --build
```

---

# 🎉 YOU ARE DONE
