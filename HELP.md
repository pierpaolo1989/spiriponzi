# Getting Started

### Reference Documentation
For further reference, please consider the following sections:

* [Official Apache Maven documentation](https://maven.apache.org/guides/index.html)
* [Spring Boot Maven Plugin Reference Guide](https://docs.spring.io/spring-boot/docs/3.2.5/maven-plugin/reference/html/)
* [Create an OCI image](https://docs.spring.io/spring-boot/docs/3.2.5/maven-plugin/reference/html/#build-image)
* [Spring Web](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/index.html#web)
* [Thymeleaf](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/index.html#web.servlet.spring-mvc.template-engines)

### Guides
The following guides illustrate how to use some features concretely:

* [Building a RESTful Web Service](https://spring.io/guides/gs/rest-service/)
* [Serving Web Content with Spring MVC](https://spring.io/guides/gs/serving-web-content/)
* [Building REST services with Spring](https://spring.io/guides/tutorials/rest/)
* [Handling Form Submission](https://spring.io/guides/gs/handling-form-submission/)

### Manual withdrawals

Users can request BTC (Bitcoin mainnet), ETH (Ethereum mainnet), or PayPal (EUR).
Users can save an optional PayPal email in their profile; it is prefilled when
choosing PayPal and can be changed for an individual withdrawal. Profile referral
links can be shared via WhatsApp, Telegram, email, the device share menu, or copied.
The application uses `APP_PUBLIC_BASE_URL` when building referral links. Hibernate
schema updates add the nullable `users.paypal_email` column; installations that
manage their schema separately must add a nullable `varchar(254)` column.
New accounts, including those registered with Google, must be activated through
the link sent to their email before they can sign in. Activation links expire
after 24 hours; users can request a replacement from the login page. Referral
rewards are credited to both users only after activation. Existing accounts
remain active.
3,000 points equal EUR 0.50, rounded down to the cent, with a fixed EUR 0.10 fee.
Requests reserve points; rejected unpaid requests return them. BTC and ETH are
converted manually at the rate available when paying.

Administrators use `/admin/withdrawals` to review and approve requests. Approval
does not send money: pay using an external wallet or PayPal, then record the actual
amount and transaction reference. Check the current balance, destination, network,
exchange rate, and real provider fees before sending; the real fees can differ
from the fixed platform fee. Record payments already sent even if a subsequent
reward reversal has made the user's balance negative. Cancel an approved request
only when no payment was sent. Legacy requests without reserved points require
manual reconciliation and can be archived without changing the balance.

Set `GOOGLE_CATPCHA_KEY` and `GOOGLE_CATPCHA_SECRET` to real reCAPTCHA credentials
before accepting withdrawal requests. The bundled Google test keys are rejected for
withdrawals by default. `WITHDRAWALS_ALLOW_TEST_CAPTCHA=true` permits them only for
local testing; never enable it in production.

### Game rewards

The games page offers 50 points per claim, no more than once every hour.
The first countdown starts when the account first requests its game claim status
or attempts a game claim. The server stores and enforces the cooldown per account,
including across sessions and concurrent requests; it does not verify active play.
Existing accounts keep their last game claim timestamp when the interval changes.
Adaptive A-ADS unit 2462874 appears at the bottom of the profile, at the top of
the reports page, and between the two rows of games.

### Prime Earn surveys

The Surveys page embeds Prime Earn's survey iframe and identifies each user by
their stable Spiriponzi user ID. In Render, set `PRIME_EARN_APP_ID` to the
Publisher App ID and `PRIME_EARN_POSTBACK_SALT` to the secret salt from the
Prime Earn dashboard. Keep the salt server-side. The optional
`PRIME_EARN_CALLBACK_URL` is the public callback base URL; its default is
`https://www.spiriponzi.cloud/callbacks/primeearn`. If the app uses another
hostname, set this to that exact HTTPS endpoint.

In the Prime Earn dashboard, configure the postback URL as
`https://www.spiriponzi.cloud/callbacks/primeearn?user={user}&tx_id={tx_id}&reward={reward}&type={type}&reconciled_tx_id={reconciled_tx_id}`
and do not add `hash` manually; Prime Earn appends it. The URL must use the
same hostname and path as `PRIME_EARN_CALLBACK_URL`. The callback verifies the
SHA-1 URL signature, processes each transaction once, credits completion,
screenout and bonus rewards, and applies reconciliation events. Test with
Prime Earn's Test Mode and Test Postback before going live. Prime Earn also
documents IP allowlisting for its postbacks; configure it at the network layer
if available, alongside signature verification.

### Render overview in the admin dashboard

The optional Render panel lists the first 100 services accessible to a workspace
API key. It reports whether each service is suspended, not its health or uptime.
It does not show usage or billing amounts; use the link to the Render workspace
Billing page for official charges, quotas and invoices.

Create a workspace-scoped Render API key with read access to services, then set
`RENDER_API_KEY` in the Render service's Environment settings and redeploy or
restart. Never commit or share the key. If unset, the rest of the admin dashboard
still works and the panel reports that it is not configured. Refresh the panel
with its button; it does not refresh automatically.

### Cloudflare Web Analytics

The Cloudflare beacon reports aggregated site traffic, including geographic
summaries, directly in Cloudflare Web Analytics. It does not write analytics
data to the application database. The homepage contains the provided site
token. To enable the beacon on the other pages, set the same token as
`CLOUDFLARE_WEB_ANALYTICS_TOKEN` in the Render service's Environment settings
and redeploy or restart. Review the applicable privacy notice and consent
requirements for your deployment. If Cloudflare is already injecting Web
Analytics automatically through its proxy, disable one of the manual beacons
to avoid counting visits twice.

To display aggregated page views by country in the admin dashboard, create a
Cloudflare API token with the **Account Analytics: Read** permission and set
`CLOUDFLARE_ANALYTICS_API_TOKEN`, `CLOUDFLARE_ACCOUNT_ID`, and
`CLOUDFLARE_WEB_ANALYTICS_TOKEN` in the Render service's Environment settings.
Use the Web Analytics site token already provided for the beacon as the value
of `CLOUDFLARE_WEB_ANALYTICS_TOKEN`; it is used as the site tag. Keep the API
token server-side and never commit or expose it. The dashboard requests the
last 30 days directly from Cloudflare; it does not persist the results in the
application database. If the panel reports an API error, check the token's
account permission and that the site token is correct.

### Sign in with Google

Google sign-in is optional and disabled by default. It creates accounts only
after the user accepts the terms on the registration page, and preserves any
valid referral code. Existing password accounts cannot be linked to Google,
even if their email matches. Google users can later sign in with Google; the
existing password recovery flow can also be used to set a password for the
verified account email. Google sign-in never grants the configured admin role.

1. In [Google Auth Platform](https://console.cloud.google.com/auth/overview),
   create or select a Google Cloud project and configure branding, support
   contact, audience, and the required app homepage, privacy policy and terms.
   In Testing mode add test users; publish the app when it is ready for its
   intended audience and complete any Google verification requirements.
2. Under Clients, create an OAuth client of type **Web application**. Add the
   exact redirect URI `https://www.spiriponzi.cloud/login/oauth2/code/google`
   (or `APP_PUBLIC_BASE_URL` followed by `/login/oauth2/code/google` for the
   domain actually used). The scheme, hostname and path must match exactly.
   Register any additional domains or local redirect URIs as required by Google.
3. In the Render web service's **Environment** settings add:
   - `GOOGLE_LOGIN_ENABLED=true`
   - `GOOGLE_CLIENT_ID` = the OAuth web client ID
   - `GOOGLE_CLIENT_SECRET` = the OAuth web client secret
   - `APP_PUBLIC_BASE_URL=https://www.spiriponzi.cloud` (adjust for the actual
     public domain; no trailing slash)
   Save and deploy the new build. Do not commit credentials. This client secret
   is separate from CAPTCHA and SMTP credentials.
4. Hibernate schema updates add a nullable unique `users.google_subject`
   `varchar(255)` column. Where schema changes are managed separately, create
   that column and unique constraint before enabling the feature.
5. Test registration with a new Google account, an optional referral, sign-out
   and subsequent sign-in. Verify an existing password account is not linked.
   For local development, set `APP_PUBLIC_BASE_URL=http://localhost:8080` and
   register `http://localhost:8080/login/oauth2/code/google` with the local
   OAuth client. Use separate Google projects for development and production.

The implementation requests OpenID Connect identity, email, and basic profile
scopes so the app can display the Google profile photo when one is available.
It does not request access to Gmail messages. The callback uses the configured
public URL rather than trusting proxy headers. Real Google authentication
requires valid Google credentials and a publicly accessible HTTPS deployment.
