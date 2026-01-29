# Cloud-Native Secure Real-Time Auction System

## Course
**ENCS 691K – Cloud Networking and Service Provisioning**  
Winter 2026

---

## Project Overview
This repository contains the implementation of a **cloud-native, secure, real-time Auction System** offered as **Software-as-a-Service (SaaS)**.

The system allows registered end-users to:
- Advertise items for auction
- Place bids in real time
- Receive live updates on the highest bid
- Automatically determine winners after a fixed auction duration

The design strictly follows the ENCS 691K project specification and leverages modern cloud technologies to ensure **scalability, elasticity, fault tolerance, and security**.

---

## System Characteristics
- **Cloud-native microservices architecture**
- **Real-time bidding and notifications**
- **Strong consistency for auction results**
- **Secure authentication and authorization**
- **Scalable and fault-tolerant deployment**

---

## High-Level Architecture
Users (Web Browser)
|
Load Balancer
|
API Gateway
|
Kubernetes Cluster
├── User Service
├── Auction Service
├── Bid Service
├── Notification Service
└── Auth Service
|
Managed Database | Redis | Message Broker

---

## Core Functional Requirements
- User registration and deregistration with constraints
- Item advertisement by registered users
- Auctions last exactly **5 minutes**
- Users may place multiple bids per auction
- Highest bid wins; ties resolved by first bid received
- Continuous broadcast of highest bid updates
- Automatic auction restart if no valid bids are received

---

## Repository Structure
auction/
├── services/ # Microservices implementation
├── frontend/ # Web-based SaaS client
├── infra/ # Kubernetes and cloud infrastructure
├── docs/ # Report, diagrams, and presentation
├── common/ # Shared contracts and utilities
└── scripts/ # Database and demo scripts

---

## Microservices Overview

| Service | Description |
|-------|-------------|
| User Service | User registration, deregistration, and state validation |
| Auction Service | Auction lifecycle and timer management |
| Bid Service | Bid validation, concurrency control, and winner resolution |
| Notification Service | Real-time bid and auction updates |
| Auth Service | Authentication and authorization |

Each service is independently deployable, stateless, and horizontally scalable.

---

## Cloud Technologies Used
- **Docker** for containerization
- **Kubernetes** for orchestration and scalability
- **Managed SQL Database** for persistent data
- **Redis** for low-latency bid processing
- **WebSockets / SSE** for real-time communication
- **JWT & TLS** for security

---

## Scalability and Fault Tolerance
- Stateless microservices with horizontal scaling
- Independent scaling of high-load services (Bid, Notification)
- Health checks and automatic restarts
- Leader election for safe auction finalization
- Resilient handling of concurrent bids

---

## Security Design
- Secure communication via HTTPS/TLS
- JWT-based authentication
- Role-based authorization
- Input validation and rate limiting
- Server-side timestamping to ensure bid integrity

---

## Local Development
A `docker-compose.yml` file is provided for local development and demonstration.  
Each service can also be built and run independently.

---

## Demo Strategy
The live demo demonstrates:
1. User registration
2. Item advertisement
3. Competing bids from multiple users
4. Real-time bid updates
5. Auction completion and winner notification

---

## Team Collaboration
This project is designed for a team of up to **5 members**, with each member responsible for one or more services, cloud deployment, or frontend integration.

---

## License
This project is developed exclusively for academic purposes as part of ENCS 691K.