# Sunu Santé : plateforme de télémédecine résiliente

![Java 17](https://img.shields.io/badge/Java-17-informational) ![Spring Boot](https://img.shields.io/badge/Spring%20Boot-microservices-informational) ![RabbitMQ](https://img.shields.io/badge/RabbitMQ-AMQP-informational) ![Docker Compose](https://img.shields.io/badge/Docker-Compose-informational) ![Prometheus](https://img.shields.io/badge/Prometheus-m%C3%A9triques-informational)

Projet final du module *Applications distribuées* (Master 2 R2SD, UGB).
Cinq microservices Spring Boot collectent les mesures de capteurs médicaux, les analysent en temps réel, alertent le médecin et conservent des dossiers médicaux répliqués sans leader, en restant fonctionnels malgré les pannes de réseau et de serveurs.

**Points techniques**

- Communication asynchrone par RabbitMQ, avec tampon local et backoff exponentiel : les mesures reçues pendant une panne du broker sont réémises à son retour
- Dossiers médicaux répliqués sur trois nœuds sans leader, par quorum (N=3, W=2, R=2), avec réparation à la lecture
- Disjoncteur Resilience4j sur la passerelle et sur l'analyse
- Métriques métier exposées à Prometheus, scénarios de panne scriptés et reproductibles

> **In English.** Sunu Santé is a resilient telemedicine platform built for the *Distributed Applications* course (Master's in Networks, Security and Distributed Systems, Gaston Berger University, Senegal). Five Spring Boot microservices ingest medical sensor readings, analyse them in real time, alert the doctor and store medical records replicated without a leader (quorum N=3, W=2, R=2). It keeps working through broker and node failures thanks to local buffering with exponential backoff, read repair and Resilience4j circuit breakers. Everything runs with `docker compose up`, and failure scenarios are scripted.

## Architecture

```
 Capteurs ──REST──▶ service-patient ──AMQP (sunu.mesures)──▶ service-analyse-temps-reel ──AMQP (sunu.alertes)──▶ service-notification
                    (tampon + backoff)                         │ règles de seuil                                  (SMS / e-mail / push simulés)
                                                               │ REST + disjoncteur
 Médecin ──REST──▶ api-gateway ──REST + disjoncteur──▶ service-dossier-medical × 3 (dossier-1, dossier-2, dossier-3)
 (console web)                                          quorum sans leader N=3, W=2, R=2
```

| Service | Port | Rôle |
|---|---|---|
| `api-gateway` | 8080 | Point d'entrée d'administration, console web du médecin, disjoncteur Resilience4j |
| `service-patient` | 8081 | Profils patients, réception des mesures, tampon local avec backoff exponentiel |
| `service-analyse-temps-reel` | 8083 | Consomme les mesures, détecte les anomalies, publie les « Alertes Critiques » |
| `service-notification` | 8084 | Consomme les alertes et notifie le médecin (simulé dans la console) |
| `dossier-medical-1/2/3` | 8091, 8092, 8093 | Source de vérité des dossiers, réplication par quorum sans leader |
| RabbitMQ | 5672, 15672 | Broker de messages (console : http://localhost:15672) |
| Prometheus | 9090 | Collecte des métriques de tous les services |

## Prérequis

- Docker Desktop (ou Docker Engine + plugin Compose), 6 Go de mémoire disponibles pour Docker.
- Facultatif, pour compiler hors Docker : JDK 17 ou plus et Maven 3.9.

## Lancement

```bash
docker compose up --build -d
docker compose ps
```

Le premier lancement télécharge les images et les dépendances Maven (quelques minutes). Ensuite :

- console du médecin : http://localhost:8080
- console RabbitMQ : http://localhost:15672 (identifiants : variables `RABBITMQ_USER` / `RABBITMQ_PASSWORD`, par défaut `sunu` / `sunu-demo` ; à changer via un fichier `.env` hors démonstration)
- Prometheus : http://localhost:9090
- métriques d'un service : http://localhost:8083/actuator/prometheus

Arrêt : `docker compose down` (ajouter `-v` pour effacer aussi les données).

Compilation et tests unitaires hors Docker : `mvn clean package` à la racine.

## Scénarios de test

Le script `scripts/demo.ps1` (PowerShell, depuis la racine du projet) enchaîne les scénarios du sujet :

```powershell
powershell -ExecutionPolicy Bypass -File scripts\demo.ps1              # scénarios 1 à 5
powershell -ExecutionPolicy Bypass -File scripts\demo.ps1 -Scenario 6  # quorum et disjoncteur
```

Équivalents manuels :

```bash
# Inscrire un patient (profil + dossier répliqué)
curl -X POST localhost:8080/api/patients -H "Content-Type: application/json" \
     -d '{"prenom":"Awa","nom":"Ndiaye","pathologie":"Diabète de type 2","medecinTraitant":"Dr Fall"}'

# 1. Mesure normale envoyée au service-patient
curl -X POST localhost:8081/mesures -H "Content-Type: application/json" \
     -d '{"patientId":"PAT-XXXXXXXX","type":"RYTHME_CARDIAQUE","valeur":78}'

# 2. Traitement par l'analyse
docker compose logs -f service-analyse-temps-reel

# 3. Mesure anormale (> 100 bpm) et alerte
curl -X POST localhost:8081/mesures -H "Content-Type: application/json" \
     -d '{"patientId":"PAT-XXXXXXXX","type":"RYTHME_CARDIAQUE","valeur":135}'
docker compose logs service-notification

# 4. Panne du broker : les mesures sont mises en tampon, puis réémises au retour de RabbitMQ
docker compose stop rabbitmq
curl localhost:8081/mesures/tampon
docker compose start rabbitmq

# 5. Métriques
curl localhost:8083/actuator/prometheus | grep mesures_capteurs_traitees_total
curl localhost:8091/actuator/prometheus | grep dossiers_medicaux_mis_a_jour_total

# 6. Quorum : une réplique arrêtée, le service répond ; deux arrêtées, le quorum est perdu
#    et le disjoncteur de la gateway s'ouvre
docker compose stop dossier-medical-3
docker compose stop dossier-medical-2
curl localhost:8080/api/systeme/etat
docker compose start dossier-medical-2 dossier-medical-3

# Version détenue par chaque réplique (diagnostic, sans quorum)
curl localhost:8091/dossiers/PAT-XXXXXXXX/repliques

# Surcharge simulée d'un nœud (latence 3 s) pour déclencher le disjoncteur sur les appels lents
curl -X POST localhost:8091/admin/surcharge -H "Content-Type: application/json" \
     -d '{"active":true,"latenceMs":3000,"tauxErreur":0}'
```

## Organisation du code

```
sunu-sante/
├── docker-compose.yml          orchestration de toute l'infrastructure
├── infra/prometheus/           configuration de la collecte des métriques
├── api-gateway/                Spring Boot : passerelle, disjoncteur, console web (static/index.html)
├── service-patient/            Spring Boot : patients, mesures, PublicateurMesures (tampon + backoff)
├── service-analyse-temps-reel/ Spring Boot : AnalyseurMesures, MoteurRegles
├── service-notification/       Spring Boot : NotificateurAlertes (idempotent)
├── service-dossier-medical/    Spring Boot : CoordinateurQuorum, StockageLocal, API interne de réplication
└── scripts/demo.ps1            scénarios de démonstration
```

## Métriques métier principales

| Métrique | Service | Signification |
|---|---|---|
| `mesures_capteurs_recues_total` | patient | mesures reçues des capteurs |
| `mesures_en_tampon` | patient | mesures en attente de publication |
| `mesures_capteurs_traitees_total{type}` | analyse | mesures analysées |
| `anomalies_detectees_total{type}` | analyse | anomalies détectées |
| `alertes_delai_detection_seconds` | analyse | délai entre la mesure et l'alerte |
| `notifications_envoyees_total{canal}` | notification | notifications envoyées |
| `dossiers_medicaux_mis_a_jour_total` | dossier | écritures confirmées par au moins W répliques |
| `quorum_ecritures_echouees_total`, `quorum_reparations_lecture_total` | dossier | quorum non atteint, réparations à la lecture |
| `resilience4j_circuitbreaker_state` | gateway, analyse | état du disjoncteur |
