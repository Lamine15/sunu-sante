# Démonstration des 5 scénarios de test du sujet (Windows PowerShell).
# Prérequis : la plateforme tourne (docker compose up --build -d) depuis la racine du projet.
# Usage : powershell -ExecutionPolicy Bypass -File scripts\demo.ps1 [-Scenario 1..6]

param([int]$Scenario = 0)

$ErrorActionPreference = "Stop"
$gateway = "http://localhost:8080/api"
$capteurs = "http://localhost:8081"
$fichierPatient = Join-Path $PSScriptRoot ".patient-demo"

function Titre($texte) { Write-Host "`n=== $texte ===" -ForegroundColor Cyan }
function Json($objet) { $objet | ConvertTo-Json -Depth 8 }
function Mesure($patientId, $type, $valeur) {
    $corps = @{ patientId = $patientId; type = $type; valeur = $valeur } | ConvertTo-Json
    Invoke-RestMethod -Method Post -Uri "$capteurs/mesures" -ContentType "application/json; charset=utf-8" -Body $corps
}

function Patient {
    if (Test-Path $fichierPatient) { return (Get-Content $fichierPatient) }
    Titre "Inscription d'un patient via l'API Gateway (profil + dossier répliqué)"
    $corps = @{ prenom = "Awa"; nom = "Ndiaye"; dateNaissance = "1968-04-12"; pathologie = "Diabète de type 2";
                medecinTraitant = "Dr Fall"; telephone = "+221 77 000 00 00" } | ConvertTo-Json
    $r = Invoke-RestMethod -Method Post -Uri "$gateway/patients" -ContentType "application/json; charset=utf-8" `
        -Body ([Text.Encoding]::UTF8.GetBytes($corps))
    Write-Host (Json $r)
    Set-Content $fichierPatient $r.patient.id
    return $r.patient.id
}

$id = Patient

if ($Scenario -in 0, 1) {
    Titre "1. Envoi d'une mesure normale au service-patient"
    Json (Mesure $id "RYTHME_CARDIAQUE" 78)
}
if ($Scenario -in 0, 2) {
    Titre "2. Traitement par service-analyse-temps-reel"
    Start-Sleep -Seconds 2
    docker compose logs --tail 5 service-analyse-temps-reel
}
if ($Scenario -in 0, 3) {
    Titre "3. Mesure anormale (135 bpm > 100) : alerte dans service-notification"
    Json (Mesure $id "RYTHME_CARDIAQUE" 135)
    Start-Sleep -Seconds 3
    docker compose logs --tail 6 service-notification
    Titre "   La mesure anormale est consignée dans le dossier médical (lecture au quorum R=2)"
    Json ((Invoke-RestMethod "$gateway/patients/$id/dossier") | Select-Object version, coordinateur, quorum, repliquesConsultees, @{n="mesuresAnormales"; e={$_.dossier.mesures}})
}
if ($Scenario -in 0, 4) {
    Titre "4. Panne du broker : arrêt de RabbitMQ, les mesures partent dans le tampon"
    docker compose stop rabbitmq
    foreach ($v in 72, 80, 140) { Json (Mesure $id "RYTHME_CARDIAQUE" $v) }
    Write-Host "Tampon : $(Json (Invoke-RestMethod "$capteurs/mesures/tampon"))"
    docker compose logs --tail 8 service-patient
    Titre "   Redémarrage de RabbitMQ : le tampon se vide (backoff exponentiel)"
    docker compose start rabbitmq
    do { Start-Sleep -Seconds 3; $t = Invoke-RestMethod "$capteurs/mesures/tampon"; Write-Host "  en tampon : $($t.mesuresEnTampon)" } while ($t.mesuresEnTampon -gt 0)
    docker compose logs --tail 4 service-patient
}
if ($Scenario -in 0, 5) {
    Titre "5. Métriques Prometheus (ouvrir aussi http://localhost:9090)"
    (Invoke-WebRequest -UseBasicParsing "http://localhost:8083/actuator/prometheus").Content -split "`n" |
        Select-String "^mesures_capteurs_traitees_total|^anomalies_detectees_total"
    (Invoke-WebRequest -UseBasicParsing "http://localhost:8091/actuator/prometheus").Content -split "`n" |
        Select-String "^dossiers_medicaux_mis_a_jour_total|^quorum_"
}
if ($Scenario -eq 6) {
    Titre "6. Quorum et disjoncteur : arrêt d'une réplique, puis de deux"
    docker compose stop dossier-medical-3
    Json (Invoke-RestMethod -Method Post -Uri "$gateway/patients/$id/notes" -ContentType "application/json; charset=utf-8" `
        -Body ([Text.Encoding]::UTF8.GetBytes((@{ auteur = "Dr Fall"; contenu = "Contrôle glycémique dans 15 jours" } | ConvertTo-Json)))) |
        Select-Object -First 30
    docker compose stop dossier-medical-2
    for ($i = 1; $i -le 8; $i++) {
        try { Invoke-RestMethod "$gateway/patients/$id/dossier" | Out-Null; Write-Host "  appel $i : OK" }
        catch { Write-Host "  appel $i : HTTP $([int]$_.Exception.Response.StatusCode) $($_.ErrorDetails.Message)" }
    }
    Json (Invoke-RestMethod "$gateway/systeme/etat")
    docker compose start dossier-medical-2 dossier-medical-3
    Write-Host "Répliques redémarrées : le disjoncteur passera en HALF_OPEN après 15 s, puis CLOSED."
}
