# Courriels de JURIKA — relais, fournisseur, domaine jurika.ma

Lot L5 (E2). Règles : CDC RG-COM-09 à RG-COM-11. Décision du directeur : **Brevo** aujourd'hui,
**Amazon SES** plus tard. Adresse d'envoi unique : `noreply@jurika.ma` (nom affiché : JURIKA).

## 1. Principe : un relais SMTP, un fournisseur interchangeable

auth-service (bienvenue, identifiants, vérification d'adresse, alertes de sécurité) et
billing-service (factures, contact commercial) envoient par **SMTP authentifié avec STARTTLS**.
Le fournisseur ne se choisit que par les variables de `.env.local`, transmises par Compose aux
deux services (contrôle : `CourrielsComposeTest`). Aucun code ne change d'un fournisseur à l'autre.

| Variable | Brevo | Amazon SES |
|---|---|---|
| `JURIKA_EMAIL_PROVIDER` | `smtp` | `smtp` |
| `SMTP_HOST` | `smtp-relay.brevo.com` | `email-smtp.<région>.amazonaws.com` |
| `SMTP_PORT` | `587` | `587` |
| `SMTP_AUTH` / `SMTP_STARTTLS` | `true` / `true` | `true` / `true` |
| `SMTP_USER` | identifiant SMTP du compte Brevo | identifiant SMTP SES (distinct des clés IAM) |
| `SMTP_PASSWORD` | clé SMTP Brevo | mot de passe SMTP SES |
| `SMTP_FROM` / `SMTP_FROM_NAME` | `noreply@jurika.ma` / `JURIKA` | idem |

`JURIKA_EMAIL_PROVIDER=log` n'envoie rien (journal seulement) : réservé au développement.
Au démarrage, auth écrit la ligne « Courriels : relais SMTP <hôte>:<port>, ... » (jamais
l'identifiant ni le mot de passe) et avertit si le relais est `localhost`.

Les secrets (`SMTP_USER`, `SMTP_PASSWORD`) ne s'écrivent que dans `.env.local` (mode 600) ; ils
ne passent jamais en argument de commande.

Bascule vers SES : remplacer les cinq valeurs SMTP dans `.env.local`, publier les enregistrements
DNS du § 3, puis `docker compose ... -p jurika-local up -d --force-recreate auth-service billing-service`.
La région SES dépend de l'hébergement AWS de la mise en production (**A_DECIDER directeur**).

## 2. Contrôle

- Le test de fumée inscrit un cabinet de test et exige que le courriel de bienvenue soit **parti**
  (`emailDelivered` de la réponse d'inscription) ; il échoue sinon. Destinataire :
  `SMOKE_CONTACT_EMAIL` (`.env.local`) s'il est défini, sinon une adresse `@jurika.test`
  (acceptée par le relais, puis rejetée par le destinataire : rebond compté par le fournisseur).
- Journal : `docker logs jurika-auth | grep "Courriels :"`.

## 3. DNS du domaine jurika.ma (à publier chez le registraire)

Un domaine n'a qu'**un seul** enregistrement SPF : pendant une transition Brevo → SES, les deux
`include` cohabitent dans le même enregistrement.

### Brevo (aujourd'hui)

Les valeurs exactes (code de vérification, cibles DKIM) sont propres au compte : elles s'affichent
dans Brevo, **Expéditeurs, domaines et IP dédiées > Domaines > Authentifier jurika.ma**. Les
recopier telles quelles ; ci-dessous la forme attendue, rien n'est inventé.

| Type | Nom | Valeur |
|---|---|---|
| TXT | `jurika.ma` | `brevo-code:<code affiché par Brevo>` |
| TXT | `jurika.ma` | `v=spf1 include:spf.brevo.com ~all` |
| CNAME | `brevo1._domainkey.jurika.ma` | `<cible DKIM 1 affichée par Brevo>` |
| CNAME | `brevo2._domainkey.jurika.ma` | `<cible DKIM 2 affichée par Brevo>` |
| TXT | `_dmarc.jurika.ma` | `v=DMARC1; p=none; rua=mailto:<boîte des rapports DMARC>; adkim=r; aspf=r` |

### Amazon SES (plus tard)

| Type | Nom | Valeur |
|---|---|---|
| CNAME ×3 | `<jeton>._domainkey.jurika.ma` | `<jeton>.dkim.amazonses.com` (trois jetons Easy DKIM affichés par SES) |
| MX | `bounce.jurika.ma` (domaine MAIL FROM personnalisé) | `10 feedback-smtp.<région>.amazonses.com` |
| TXT | `bounce.jurika.ma` | `v=spf1 include:amazonses.com ~all` |
| TXT | `jurika.ma` | SPF unique : `v=spf1 include:spf.brevo.com include:amazonses.com ~all` pendant la transition, puis sans Brevo |
| TXT | `_dmarc.jurika.ma` | inchangé |

### DMARC : montée en charge

1. `p=none` avec rapports (`rua`) pendant au moins deux semaines : vérifier que tous les envois
   légitimes passent SPF ou DKIM alignés.
2. `p=quarantine; pct=25`, puis `pct=100`.
3. `p=reject` quand les rapports ne montrent plus d'échec légitime.

La boîte qui reçoit les rapports DMARC (`rua`) est à choisir par la direction (**A_DECIDER**).
