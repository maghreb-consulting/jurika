# Courriels de JURIKA — relais, fournisseur, domaine jurika.ma

Lot L5 (E2). Règles : CDC RG-COM-09 à RG-COM-11. Décision du directeur : **Brevo** aujourd'hui,
**Amazon SES** plus tard. Nom affiché de l'expéditeur : JURIKA.

**Lot L6 (E5), décision de l'utilisateur du 2026-10-13 : le domaine jurika.ma n'est pas déployé et ne
le sera qu'à la mise en production.** Rien n'est configuré pour jurika.ma ni pour jurika.org avant
cette date. En test, l'expéditeur est l'adresse vérifiée dans Brevo par l'utilisateur, définie dans
`.env.local` (`SMTP_FROM`), avec le nom affiché « JURIKA » (`SMTP_FROM_NAME`). Il n'existe plus
d'adresse d'expéditeur par défaut : sans `SMTP_FROM`, Compose refuse de démarrer la pile, et auth
refuse d'envoyer en le disant. Le § 3 ci-dessous ne s'applique qu'à la mise en production.

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
| `SMTP_FROM` / `SMTP_FROM_NAME` | adresse vérifiée dans Brevo (test) / `JURIKA` | adresse du domaine vérifié dans SES / `JURIKA` |

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
  (`verifyEmailSent` de la réponse d'inscription : accepté par le relais) ; il échoue sinon.
  Destinataire : `SMOKE_CONTACT_EMAIL` (`.env.local`) s'il est défini, en sous-adresse
  `+smoke-<horodatage>`, sinon une adresse `@jurika.test` (acceptée par le relais, puis rejetée
  par le destinataire : rebond compté par le fournisseur).
- Journal : `docker logs jurika-auth | grep "Courriels :"`.

## 2 bis. Courriels au client (lot L5, E9, RG-COM-09)

Le client est averti **par courriel** (jamais dans l’application) : invitation et identifiants,
requête du cabinet, complément demandé, document mis à sa disposition, réponse à sa demande.
Chaque courriel part de l’adresse unique ci-dessus et **nomme le cabinet** à l’origine
(sujet et corps). Les gestes faits dans la Data Room (dataroom-service) publient un évènement
`courriel.client.<type>` sur l’échange RabbitMQ `jurika.events`, après validation de la
transaction ; auth-service le consomme (file `auth.courriels-client`), vérifie le destinataire
(compte client actif du cabinet) et envoie par son relais.

Un courriel qui ne part pas (destinataire refusé, relais injoignable) est journalisé en ERROR
(« Courriel client NON envoye ») puis rangé **intact** dans la file `auth.courriels-client.echecs`,
consultable et rejouable depuis la console RabbitMQ ; il n’est ni perdu ni rejoué en boucle.

## 2 ter. Mise en page et exemplaires (recette R3)

- **Une seule mise en page** pour tous les courriels (auth-service et billing-service) :
  `backend-java/jurika-common/src/main/resources/templates/email/_mise-en-page.html` (bandeaux
  marine et or, frise de zellige en caractères, logo, titre). Chaque gabarit n’y ajoute que son
  corps et son pied.
- **Aucune image de décor** : le zellige est fait de caractères et de couleurs. La **seule image**
  est le logo Moucharabieh en PNG 3x (`branding/courriel/logo-jurika-courriel.png`, 168 × 168,
  affiché en 56 × 56, texte de remplacement « JURIKA »), joint **en ligne** (Content-ID
  `logo-jurika`) et référencé par le HTML. Régénération depuis le SVG du front :
  `node scripts/courriels/generer-logo-courriel.mjs`.
- **Structure MIME** (`CourrielJurika`, jurika-common) : `multipart/related` [ `multipart/alternative`
  (texte brut + HTML), logo en ligne ]. Seuls la facture et le contrat ajoutent leur **PDF en pièce
  jointe** (racine `multipart/mixed`). Toute autre image en ligne et toute image de fond CSS sont
  refusées. Cause du défaut corrigé : le motif de zellige était joint en ligne sans être référencé
  par aucun gabarit, et Gmail comme Outlook affichent une telle partie comme pièce jointe.
- **Exemplaires** (environnement de test seulement : `JURIKA_TEST_SEED_ENABLED=true` et profil
  autre que `prod`) : `POST /api/v1/test/courriels/auth` (bienvenue, identifiants d’un employé,
  identifiants d’un client, réinitialisation du mot de passe, notification au client) et
  `POST /api/v1/test/courriels/facturation` (facture avec son PDF), corps
  `{"destinataire": "adresse"}`. Données fictives explicites, objet préfixé « [Exemplaire fictif] ».

## 3. DNS du domaine jurika.ma (à la mise en production seulement, à publier chez le registraire)

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
