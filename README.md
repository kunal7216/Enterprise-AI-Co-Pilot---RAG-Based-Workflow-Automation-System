
# Enterprise AI Co-Pilot · RAG-Based Workflow Automation System

Enterprise AI Co-Pilot is a Java Spring Boot backend system for document-based workflow automation. It processes invoice and document uploads, extracts structured information using a local LLM, detects fraud signals, retrieves similar historical workflows using pgvector, and generates explainable approval decisions.

The project demonstrates how AI, RAG, rule-based fraud detection, authentication, and backend workflow orchestration can be combined in a practical enterprise-style application.

---

## Overview

Many business workflows depend on manual document review, invoice validation, approval routing, and fraud checks. This process is often slow, inconsistent, and difficult to audit.

Enterprise AI Co-Pilot automates this workflow by combining:

- Document text extraction
- Local LLM-based field extraction
- Rule-based fraud detection
- RAG-based historical workflow retrieval
- Explainable approval decisions
- JWT-secured APIs
- Docker-based local deployment

The system is designed as a backend-focused project to demonstrate Java, Spring Boot, PostgreSQL, pgvector, LLM integration, and workflow decision logic.

---

## Key Features

- **Document upload and processing** for PDF, DOCX, XLSX, and TXT files
- **LLM-based field extraction** using Ollama
- **RAG retrieval** using PostgreSQL and pgvector
- **Fraud detection rules** for duplicate invoices, missing fields, unknown vendors, round amounts, weekend invoices, and amount spikes
- **Explainable decision engine** for approve, escalate, or reject decisions
- **Workflow history tracking** for auditability
- **JWT authentication and role-based access control**
- **Swagger/OpenAPI documentation**
- **Docker Compose setup** for local execution

---

## System Architecture

```text
User Upload
    |
    v
Document Text Extraction
    |
    v
LLM Field Extraction
    |
    v
Fraud Detection Engine
    |
    v
Decision Engine
    |
    v
Embedding Generation
    |
    v
PostgreSQL + pgvector Storage
    |
    v
RAG Context Retrieval
    |
    v
Explainable Workflow Decision
````

---

## Request Processing Flow

```text
1. User uploads an invoice or document.
2. Backend extracts text from the uploaded file.
3. Ollama LLM extracts structured fields such as vendor, amount, invoice date, and invoice number.
4. Fraud detection rules evaluate risk signals.
5. Embeddings are generated for semantic retrieval.
6. pgvector retrieves similar historical workflows.
7. Decision engine combines extracted fields, fraud risk, confidence score, and RAG context.
8. System returns an explainable approval, escalation, or rejection decision.
```

---

## Core Modules

### 1. Document Processing

The document processing module extracts text from uploaded files and prepares the extracted content for AI-based field extraction.

Supported file types:

* PDF
* DOCX
* XLSX
* TXT

---

### 2. LLM-Based Field Extraction

The system uses Ollama to extract structured fields from unstructured document text.

Example extracted fields:

```json
{
  "vendorName": "ABC Supplies",
  "invoiceNumber": "INV-1023",
  "amount": 24500,
  "invoiceDate": "2026-05-10",
  "category": "Office Supplies"
}
```

---

### 3. Fraud Detection Engine

The fraud detection engine applies deterministic rules to identify suspicious workflow patterns.

Fraud signals include:

* Missing required fields
* Unknown vendor
* Duplicate invoice
* Round amount
* Weekend invoice date
* Amount spike compared to historical workflows

Example decision factors:

```text
Unknown vendor      -> increases risk
Duplicate invoice   -> high-risk signal
Large amount        -> escalation condition
Low confidence      -> manual review required
Clean invoice       -> eligible for auto-approval
```

---

### 4. RAG-Based Retrieval

The system uses PostgreSQL with pgvector to store workflow embeddings and retrieve semantically similar historical workflows.

RAG helps the decision engine compare a new workflow with previous records, improving decision context and explainability.

Example use cases:

* Find similar invoices from the same vendor
* Compare current invoice amount with historical patterns
* Retrieve past approvals or escalations
* Provide contextual evidence for decision-making

---

### 5. Explainable Decision Engine

The decision engine combines rule-based checks, AI extraction confidence, fraud score, amount thresholds, and RAG insights.

Possible decisions:

```text
AUTO_APPROVED
ESCALATED
REJECTED
```

Example decision rules:

```text
Low confidence       -> ESCALATED
High fraud risk      -> ESCALATED
Large amount         -> ESCALATED
Duplicate invoice    -> ESCALATED / REJECTED
Clean + high confidence -> AUTO_APPROVED
```

Each workflow stores explanation fields such as:

* Decision reason
* AI recommendation
* Fraud risk summary
* Review comments
* Retrieved RAG context

---

## Tech Stack

| Category          | Technology             |
| ----------------- | ---------------------- |
| Language          | Java 17                |
| Framework         | Spring Boot 3          |
| Database          | PostgreSQL             |
| Vector Search     | pgvector               |
| AI / LLM          | Ollama                 |
| Embedding Model   | nomic-embed-text       |
| Authentication    | JWT                    |
| API Documentation | Swagger / OpenAPI      |
| Deployment        | Docker, Docker Compose |
| Build Tool        | Maven                  |

---

## Project Structure

```text
src/main/java/com/enterprise/copilot/
├── config/
├── controller/
├── dto/
├── entity/
├── repository/
├── security/
├── service/
│   ├── WorkflowService
│   ├── OllamaService
│   ├── FraudDetectionService
│   ├── RagService
│   └── DocumentExtractionService
└── EnterpriseCopilotApplication.java
```

---

## API Endpoints

### Authentication

| Method | Endpoint                | Description                      |
| ------ | ----------------------- | -------------------------------- |
| POST   | `/api/v1/auth/register` | Register a new user              |
| POST   | `/api/v1/auth/login`    | Authenticate user and return JWT |

### Workflows

| Method | Endpoint                               | Description                   |
| ------ | -------------------------------------- | ----------------------------- |
| POST   | `/api/v1/workflows/upload`             | Upload and process a document |
| GET    | `/api/v1/workflows`                    | List workflows                |
| GET    | `/api/v1/workflows/{id}`               | Get workflow details          |
| POST   | `/api/v1/workflows/approve`            | Approve or review workflow    |
| GET    | `/api/v1/workflows/stats`              | Get workflow statistics       |
| GET    | `/api/v1/workflows/{id}/history`       | View workflow history         |
| GET    | `/api/v1/workflows/{id}/rag-insights`  | Retrieve RAG-based insights   |
| GET    | `/api/v1/workflows/dashboard/insights` | View dashboard insights       |

---

## Getting Started

### Prerequisites

Make sure the following are installed:

* Java 17+
* Maven
* Docker
* Docker Compose

---

## Run Locally

### 1. Clone the Repository

```bash
git clone https://github.com/kunal7216/enterprise-ai-copilot.git
cd enterprise-ai-copilot
```

### 2. Build the Project

```bash
mvn clean install
```

### 3. Start the System

```bash
docker compose up --build -d
```

### 4. Enable pgvector Extension

Run the following commands once after the database container starts:

```bash
docker exec -it postgres_db psql -U postgres -d enterprise_copilot -c "CREATE EXTENSION IF NOT EXISTS vector;"
```

If required, add workflow vector columns manually:

```bash
docker exec -it postgres_db psql -U postgres -d enterprise_copilot -c "ALTER TABLE workflows ADD COLUMN IF NOT EXISTS embedding vector(384);"
docker exec -it postgres_db psql -U postgres -d enterprise_copilot -c "ALTER TABLE workflows ADD COLUMN IF NOT EXISTS decision_reason TEXT;"
```

### 5. Pull Ollama Models

```bash
docker exec -it enterprise-ollama ollama pull llama3
docker exec -it enterprise-ollama ollama pull nomic-embed-text
```

---

## Access the Application

Swagger UI:

```text
http://localhost:8080/swagger-ui.html
```

Health Check:

```text
http://localhost:8080/actuator/health
```

---

## Docker Services

```text
enterprise-app      -> Spring Boot backend
postgres_db         -> PostgreSQL database with pgvector
enterprise-ollama   -> Local Ollama LLM service
```

---

## Configuration

### Database

```properties
SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/enterprise_copilot
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=postgres
```

### Ollama

```properties
OLLAMA_BASE_URL=http://ollama:11434
OLLAMA_MODEL=llama3
OLLAMA_EMBEDDING_MODEL=nomic-embed-text
```

---

## Example Test Flow

### 1. Register User

```json
{
  "username": "admin",
  "email": "admin@test.com",
  "password": "admin123",
  "fullName": "Admin User"
}
```

### 2. Login

Use the login endpoint to generate a JWT token.

```http
POST /api/v1/auth/login
```

### 3. Authorize in Swagger

Click **Authorize** in Swagger UI and provide:

```text
Bearer <your-jwt-token>
```

### 4. Upload Document

```http
POST /api/v1/workflows/upload
```

Supported upload formats:

* PDF
* DOCX
* XLSX
* TXT

### 5. Review Response

Example response:

```json
{
  "confidenceScore": 0.87,
  "decisionType": "AUTO_APPROVED",
  "decisionReason": "Invoice is below approval threshold and no high-risk fraud indicators were detected.",
  "aiRecommendation": "Invoice appears valid based on extracted fields and historical workflow context.",
  "ragInsights": [
    "Similar invoice approved for same vendor in previous workflow."
  ]
}
```

---

## Testing and Validation

Suggested validation scenarios:

* Upload valid invoice
* Upload invoice with missing fields
* Upload duplicate invoice
* Upload invoice with unknown vendor
* Upload weekend-dated invoice
* Upload high-value invoice
* Upload invoice with round amount
* Compare workflow with historical RAG context
* Verify JWT-protected routes
* Validate workflow history after decision update



## Design Decisions

### Why Ollama?

Ollama allows local LLM execution without relying on external paid APIs. This makes the project easier to run, test, and demonstrate locally.

### Why pgvector?

pgvector enables semantic similarity search directly inside PostgreSQL, allowing workflow records and embeddings to remain in the same database.

### Why combine RAG with fraud rules?

Fraud rules provide deterministic and explainable checks, while RAG adds historical context from similar workflows. Combining both improves interpretability and decision quality.

### Why JWT authentication?

JWT provides stateless authentication for REST APIs and supports role-based workflow access.

### Why Docker Compose?
Docker Compose allows the backend, database, and local LLM service to run together in a repeatable local environment.

---



## Author

**Kunal Kumar**
GitHub: [kunal7216](https://github.com/kunal7216)


