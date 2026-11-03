# Jeu de données de démonstration : 3 patients, des mesures (dont quelques anomalies),
# une prescription et une note. Usage : powershell -ExecutionPolicy Bypass -File scripts\peupler.ps1

$ErrorActionPreference = "Stop"
$gateway = "http://localhost:8080/api"
$capteurs = "http://localhost:8081"

function Poster($url, $objet) {
    $octets = [Text.Encoding]::UTF8.GetBytes(($objet | ConvertTo-Json))
    Invoke-RestMethod -Method Post -Uri $url -ContentType "application/json; charset=utf-8" -Body $octets
}

$patients = @(
    @{ prenom = "Awa"; nom = "Ndiaye"; dateNaissance = "1968-04-12"; pathologie = "Diabète de type 2"; medecinTraitant = "Dr Fall" },
    @{ prenom = "Moussa"; nom = "Diop"; dateNaissance = "1955-09-02"; pathologie = "Hypertension artérielle"; medecinTraitant = "Dr Sarr" },
    @{ prenom = "Fatou"; nom = "Sow"; dateNaissance = "1990-01-23"; pathologie = "Insuffisance cardiaque"; medecinTraitant = "Dr Fall" }
) | ForEach-Object { (Poster "$gateway/patients" $_).patient }
$patients | ForEach-Object { Write-Host "Inscrit : $($_.id) $($_.prenom) $($_.nom)" }
$awa, $moussa, $fatou = $patients

$aleatoire = New-Object Random
for ($i = 0; $i -lt 30; $i++) {
    Poster "$capteurs/mesures" @{ patientId = $awa.id; type = "GLUCOSE"; valeur = [Math]::Round(0.9 + $aleatoire.NextDouble() * 1.1, 2) } | Out-Null
    Poster "$capteurs/mesures" @{ patientId = $moussa.id; type = "TENSION_SYSTOLIQUE"; valeur = 115 + $aleatoire.Next(35) } | Out-Null
    Poster "$capteurs/mesures" @{ patientId = $fatou.id; type = "RYTHME_CARDIAQUE"; valeur = 68 + $aleatoire.Next(40) } | Out-Null
    Start-Sleep -Milliseconds 700
}
Poster "$gateway/patients/$($awa.id)/prescriptions" @{ medicament = "Metformine 850 mg"; posologie = "1 comprimé matin et soir"; prescripteur = "Dr Fall" } | Out-Null
Poster "$gateway/patients/$($awa.id)/notes" @{ auteur = "Dr Fall"; contenu = "Glycémie instable : contrôle dans 15 jours, renforcer le régime." } | Out-Null
Write-Host "Données de démonstration créées. Console : http://localhost:8080/#$($awa.id)"
Set-Content (Join-Path $PSScriptRoot ".patient-demo") $awa.id
