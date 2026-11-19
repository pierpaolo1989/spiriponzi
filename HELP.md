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
