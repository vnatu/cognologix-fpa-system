# Spring Boot 3.3.2 → 4.1.x upgrade assessment

Read-only assessment of `backend/` at commit `07d9d03` (`feature_bank_statement`). No application code, database, or Flyway run was changed. Trial worktrees were created outside the repo and removed after the compile.

Project check: working directory `/Users/vnatu/projects/cognologix/cognologix-fpa-system`, parent `spring-boot-starter-parent` 3.3.2, Java packages `com.cognologix.fpa` (400 package declarations, no other packages, no Chitragupta).

## Versions used

Looked up from Maven Central and the Boot BOMs, not from memory.

| Line | Version used here | Source |
| --- | --- | --- |
| Boot 3.5 latest | **3.5.16** | `spring-boot-starter-parent` maven-metadata.xml. Metadata `latest` is `4.2.0-M2` (a milestone). Newest 4.1.x is **4.1.1**. |
| Boot 4.1 | **4.1.1** | same metadata |
| Boot 4.1.1 managed | Framework **7.0.9**, Security **7.1.1**, Hibernate **7.4.5.Final**, Flyway **12.4.0**, Jackson BOM **3.1.5**, PostgreSQL **42.7.13**, Testcontainers **2.0.5**, Servlet **6.1.0** | `spring-boot-dependencies-4.1.1.pom` |
| Boot 3.5.16 managed | Framework **6.2.19**, Security **6.5.11**, Hibernate **6.6.53.Final**, Flyway **11.7.2**, Jackson **2.21.4**, PostgreSQL **42.7.11**, Testcontainers **1.21.4** | `spring-boot-dependencies-3.5.16.pom` |
| Boot 3.3.2 managed (current tree) | Framework **6.1.11**, Security **6.3.1**, Hibernate **6.5.2.Final**, Flyway **10.10.0**, Jackson **2.17.2**, PostgreSQL **42.7.3**, Testcontainers **1.19.8** | `dependency:tree` and the cached 3.3.2 BOM |

Official guides read: [Spring Boot 4.0 Migration Guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide) (edited 28 Jun 2026), [Spring Boot 4.1 Release Notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.1-Release-Notes) (edited 16 Sep 2026), [Spring Security 7.0 migration index](https://docs.spring.io/spring-security/reference/migration/index.html), [Spring AI upgrade notes](https://docs.spring.io/spring-ai/reference/upgrade-notes.html), [Spring AI 2.0 getting started](https://docs.spring.io/spring-ai/reference/getting-started.html) (“2.0.x supports Spring Boot 4.0.x and 4.1.x”), [Hibernate ORM 7.0 migration guide](https://docs.jboss.org/hibernate/orm/7.0/migration-guide/migration-guide.html), [Spring Modulith appendix](https://docs.spring.io/spring-modulith/reference/appendix.html), Modulith 2.0 blog (21 Nov 2025) and the 2.1.1 GitHub release.

Java 21 is already the project baseline. Boot 4.0 requires Java 17 and Jakarta EE 11 / Servlet 6.1. This app uses the embedded Tomcat starter, so there is no external-container move.

## Dependency inventory

`mvn dependency:tree` from `backend/` (tree written only to `/tmp`, 6 Oct 2026). “Boot-managed” means the version comes from `spring-boot-starter-parent` with no version tag in this POM.

| Direct dependency | Current | Boot-managed | First release that supports Boot 4.1 / Framework 7 |
| --- | --- | --- | --- |
| `spring-boot-starter-web` | 3.3.2 | yes | Still published in 4.1.1, description says deprecated in favor of `spring-boot-starter-webmvc`. Both POMs exist on Maven Central for 4.1.1. |
| `spring-boot-starter-security` | 3.3.2 | yes | Same artifact in 4.1.1. Test companion the guide asks for: `spring-boot-starter-security-test`. |
| `spring-boot-starter-data-jpa` | 3.3.2 | yes | Same artifact in 4.1.1. Pulls Hibernate 7.4.5.Final. |
| `spring-boot-starter-validation` | 3.3.2 | yes | Same artifact. |
| `spring-boot-starter-mail` | 3.3.2 | yes | Same artifact. |
| `spring-boot-starter-test` | 3.3.2 | yes | Same artifact. Guide also offers `spring-boot-starter-test-classic` as a temporary classpath. |
| `spring-boot-testcontainers` | 3.3.2 | yes | Same artifact, brings Testcontainers 2.0.5. |
| `spring-security-test` | 6.3.1 | yes (no version tag) | 7.1.1 via the 4.1.1 BOM. |
| `flyway-core` | 10.10.0 | yes | Boot 4.0 guide: replace the bare Flyway dependency with `spring-boot-starter-flyway`. 4.1.1 manages Flyway **12.4.0**. The starter POM depends on `spring-boot-flyway`, which depends on `flyway-core`. It does not pull the Postgres module. |
| `flyway-database-postgresql` | 10.10.0 | yes | Keep it. 4.1.1 manages 12.4.0. |
| `postgresql` (JDBC) | 42.7.3 | yes | 42.7.13 via the 4.1.1 BOM. |
| `spring-modulith-starter-core` | 1.2.2 | own BOM | **2.0.0** is the first line on Boot 4 / Framework 7 (2.0 GA, 21 Nov 2025). Appendix: 1.2 is compiled against Boot 3.3, 1.4 against Boot 3.5. **2.1.1** release notes say “Upgrade to Spring Boot 4.1.1” and Framework 7.0.9. |
| `spring-modulith-events-api` | 1.2.2 | own BOM | Same 2.0.0 / 2.1.1 line. |
| `spring-modulith-starter-test` | 1.2.2 | own BOM | Same. |
| `spring-ai-starter-model-ollama` | 1.0.0 | Spring AI BOM | **2.0.0**. 2.0.1 root POM pins `spring-boot.version` **4.1.1**. Getting-started page: 2.0.x supports Boot 4.0.x and 4.1.x. |
| `spring-ai-starter-model-openai` | 1.0.0 | Spring AI BOM | Same, 2.0.0 / 2.0.1. |
| `spring-ai-starter-vector-store-pgvector` | 1.0.0 | Spring AI BOM | Artifact still exists in the 2.0.1 BOM. |
| `jjwt-api` / `jjwt-impl` / `jjwt-jackson` | 0.12.6 | no | Latest release is **0.13.0** (20 Aug 2025). It still uses Jackson 2. Jackson 3 support is an open request ([jwtk/jjwt#1029](https://github.com/jwtk/jjwt/issues/1029)) and is not in 0.13.0. Boot 4 still manages a Jackson 2 BOM and `jjwt-jackson` brings `jackson-databind` 2.x under `com.fasterxml.jackson`. Trial B compiled the JWT classes. |
| `springdoc-openapi-starter-webmvc-ui` | 2.5.0 | no | **3.0.0** (21 Nov 2025) is “Upgrade to Spring Boot 4.0.0”. Current site version is **3.1.1** (6 Sep 2026) and its POM depends on Boot 4 module names (`spring-boot-tomcat`, `spring-boot-health`). The 3.1.1 notes do not name Boot 4.1. |
| `poi-ooxml` | 5.3.0 | no | No Spring baseline. Latest on Maven Central is 5.5.1. Trial B did not fail on POI. |
| `commons-text` | 1.12.0 | no | No Spring baseline. Latest is 1.15.0. |
| `lombok` | 1.18.34 | yes | 1.18.46 in both the 3.5.16 and 4.1.1 BOMs. |
| `org.testcontainers:junit-jupiter` | 1.19.8 | yes | Testcontainers **2.0.0** (14 Oct 2025) renamed modules to `testcontainers-*`. `org.testcontainers:junit-jupiter:2.0.5` is not on Maven Central (GET 404). Use `testcontainers-junit-jupiter`. |
| `org.testcontainers:postgresql` | 1.19.8 | yes | Same rename. 2.0.5 artifact is `testcontainers-postgresql`. The jar still contains `org.testcontainers.containers.PostgreSQLContainer` (deprecated) and `org.testcontainers.postgresql.PostgreSQLContainer`. |

Transitive `com.pgvector:pgvector` is **0.1.6** via `spring-ai-pgvector-store` 1.0.0. Maven Central’s newest `com.pgvector:pgvector` is also 0.1.6. No Java source references `PgVectorStore`. `PgVectorStoreAutoConfiguration` is excluded in `application.yml`.

### Starters in use and the Boot 4 replacement

| Used now | Boot 4.1 replacement | Applied in Trial B |
| --- | --- | --- |
| `spring-boot-starter-web` | `spring-boot-starter-webmvc` (old name still resolves, marked deprecated) | yes |
| `spring-boot-starter-security` | same name; add `spring-boot-starter-security-test` for `@WithMockUser` | name kept |
| `spring-boot-starter-data-jpa` | same name | kept |
| `spring-boot-starter-validation` | same name | kept |
| `spring-boot-starter-mail` | same name | kept |
| `spring-boot-starter-test` | same name, or `spring-boot-starter-test-classic` short term | kept |
| `flyway-core` | `spring-boot-starter-flyway` | yes, and `flyway-database-postgresql` kept |
| `spring-boot-testcontainers` | same name | kept |
| `org.testcontainers:junit-jupiter` | `testcontainers-junit-jupiter` | yes, required for resolution |
| `org.testcontainers:postgresql` | `testcontainers-postgresql` | yes, required for resolution |
| `spring-boot-starter-aop` | renamed to `spring-boot-starter-aspectj` | not direct; comes in through data-jpa |

### Blockers

No direct dependency is missing a release that can resolve on Boot 4.1.1.

These are required upgrades, and they are not blockers because a compatible release exists:

- Spring AI 1.0.0 → 2.0.1
- Spring Modulith 1.2.2 → 2.1.1 for Boot 4.1.1 (2.0.0 is the first Boot 4 line)
- springdoc 2.5.0 → 3.0.0 or later (3.1.1 is current). Trial B left 2.5.0 in place. Nothing in `src/` imports springdoc, so compile did not fail. Swagger UI is permit-all in `SecurityConfig`, so startup with 2.5.0 on Boot 4 is a runtime risk.

JJWT has no Jackson 3 module as of 0.13.0. That is a gap, and the trial still compiled `JwtTokenProvider` because Jackson 2 stays on the classpath.

## Code counts

Commands were run from `backend/`. Counts are files, then occurrences.

### Jackson 3

Commands:

```
rg -l 'com\.fasterxml\.jackson' src --glob '*.java'
rg -c 'com\.fasterxml\.jackson' src --glob '*.java'
rg -c 'ObjectMapper' src --glob '*.java'
rg -n '@JsonComponent|JsonSerializer|JsonDeserializer|@JacksonComponent' src --glob '*.java'
rg -l '@Json' src --glob '*.java'
```

| | Files | Occurrences | Size | Risk |
| --- | ---: | ---: | --- | --- |
| `com.fasterxml.jackson` imports | 5 | 7 | S | low |
| `ObjectMapper` mentions | 5 | 11 | S | low at compile, medium at runtime |
| Custom serializer / deserializer / `@JsonComponent` | 0 | 0 | S | low |
| `@Json*` annotations | 0 | 0 | S | low |
| `MappingJackson2HttpMessageConverter` | 1 | 1 | S | low at compile |

All five import files are tests: `AuthAndUserManagementIntegrationTest`, `PeoplePayrollEndToEndIntegrationTest`, `PeriodControllerTest`, `ImportControllerTest`, `CustomerControllerTest`. They `@Autowired ObjectMapper` (and two of them also `new ObjectMapper()`). Boot 4’s preferred mapper is Jackson 3 `JsonMapper` (`tools.jackson`). `MappingJackson2HttpMessageConverter` is still in `spring-web` 7.0.9, and Trial B did not report it. The annotations module stays `com.fasterxml.jackson.annotation` in Jackson 3, and this code has no Jackson annotations.

Top files by `ObjectMapper` count: `AuthAndUserManagementIntegrationTest` (3), then the other four tests (2 each).

### Spring Security 7

Commands:

```
rg -l 'org\.springframework\.security' src --glob '*.java'
rg -c 'org\.springframework\.security|requestMatchers|csrf|cors|OncePerRequestFilter|SecurityFilterChain' src --glob '*.java'
rg -n 'WebSecurityConfigurerAdapter|antMatchers|mvcMatchers|authorizeRequests\(' src --glob '*.java'
```

| | Files | Occurrences | Size | Risk |
| --- | ---: | ---: | --- | --- |
| Security imports | 21 | — | S | low |
| Filter chain, CORS, CSRF, JWT filter | 3 config/filter files | SecurityConfig 20, TestSecurityConfig 12, JwtAuthenticationFilter 5 | S | low |
| Removed 5.x/6.x APIs (`WebSecurityConfigurerAdapter`, `antMatchers`, `authorizeRequests`) | 0 | 0 | S | low |

`SecurityConfig` is the Security 6 lambda style: `csrf(AbstractHttpConfigurer::disable)`, `cors(...)`, `sessionManagement` STATELESS, `authorizeHttpRequests` / `requestMatchers`, `addFilterBefore` a `OncePerRequestFilter`. `JwtAuthenticationFilter` reads `Authorization: Bearer` and sets `SecurityContextHolder`. JJWT signs with `javax.crypto.SecretKey`, which is a JDK type. Trial B compiled these files (they are not in the error list). Boot 4.1.1 brings Security **7.1.1**. The Security 7.0 migration index’s required step for this app is the Jackson 2 → 3 change for `SecurityJackson2Modules`. This app does not use those modules.

Top files: `SecurityConfig.java`, `TestSecurityConfig.java`, `JwtAuthenticationFilter.java`, `AuthController.java`, `DatabaseUserDetailsService.java`, then controllers that only mention security annotations (`GeneralExceptionHandler`, `SystemBackupController`, `RevenueController`, `PeriodController`, and the integration test).

The servlet section of the Security 7 migration guide returned 404. See Unverified.

### Spring Framework 7 and Boot 4 removals

Commands:

```
rg -n '@MockBean|@SpyBean' src --glob '*.java'
rg -n 'RestTemplate|WebClient' src --glob '*.java'
rg -n 'import javax\.' src --glob '*.java'
```

`application.yml` (main and test) keys: `spring.servlet.multipart`, `spring.datasource`, `spring.jpa.*` (`ddl-auto: none`, `open-in-view: false`), `spring.flyway.*`, `spring.autoconfigure.exclude`, `spring.ai.*`, `server.port`. No profile-specific YAML files.

| | Files | Occurrences | Size | Risk |
| --- | ---: | ---: | --- | --- |
| `@MockBean` | 10 | 12 | M | medium (compile break on Boot 4) |
| `@SpyBean` | 0 | 0 | S | low |
| `RestTemplate` / `WebClient` | 0 | 0 | S | low |
| `import javax.*` | 2 | 3 | S | low |
| Boot 4 renamed properties in the YAML | 0 matched from the 4.0 guide | 0 | S | low |

`javax.*` here is `javax.crypto.SecretKey` and `javax.xml.parsers` in `TallyLedgerXmlParser`. Those stay in the JDK. There is no `javax.servlet` or `javax.persistence`.

`@MockBean` files, by count: `ImportControllerTest` (2), `BankReconServiceIntegrationTest` (2), then one each in `PeriodControllerTest`, `PeoplePayrollEndToEndIntegrationTest`, `PeopleModuleIntegrationTest`, `MasterDataControllerTest`, `EmployeeRegistryControllerTest`, `ClassificationConfigControllerTest`, `GeneralConfigControllerTest`, `CustomerControllerTest`. Boot 4.0 removes `@MockBean` / `@SpyBean` in favor of `@MockitoBean` / `@MockitoSpyBean` (`org.springframework.test.context.bean.override.mockito`, confirmed in `spring-test` 7.0.9).

`spring.ai.openai.chat.options.model` still uses the `.options` segment. Spring AI 2.0 flattens that to `spring.ai.openai.chat.model` and keeps the old key as deprecated. `spring.ai.ollama.embedding.model` is already flat.

### Hibernate and JPA

Boot 4.1.1 uses Hibernate **7.4.5.Final** (from 6.5.2.Final). The 7.0 guide’s baseline is Java 17 and Jakarta Persistence 3.2. Removed APIs checked and absent: `@Where`, `@Proxy`, `@Type`, `Session#save` / `saveOrUpdate` / `delete`.

Commands:

```
rg -n 'UserType|AttributeConverter|JdbcTypeCode|@Type\b' src --glob '*.java'
rg -n 'select new ' src --glob '*.java'
rg -n 'nativeQuery = true|createNativeQuery' src --glob '*.java'
rg -n 'GENERATED' src/main/resources/db --glob '*.sql'
```

| | Files | Occurrences | Size | Risk |
| --- | ---: | ---: | --- | --- |
| Custom type (`@JdbcTypeCode(SqlTypes.ARRAY)` on `integer[]`) | 1 (`Contract.java`) | 1 | S | medium |
| `columnDefinition = "TEXT"` | 12 | 19 | S | low |
| JPQL `select new` | 2 | 2 | S | medium |
| Native queries | 4 | 6 | S | low |
| SQL `GENERATED ALWAYS AS ... STORED` | 2 | 5 | S | medium |
| Entities with `@GeneratedValue` | many | — | S | low |

`select new` is in `ContractDocumentRepository` and `ContractTemplateDocumentRepository`. Native SQL is in `ReconRunRepository`, `TallyLedgerRepository`, `ContractRepository` (`nextval`), and `ContractModuleBackup` (3 `createNativeQuery` calls).

Generated columns are payroll totals in `V22__payroll_employer_contributions.sql` (2) and `V31__total_payroll_cost_net_pay.sql` (3). `ddl-auto` is `none`, so Hibernate does not create them. `V33` matches the word “generated” only as column names `generated_at` / `generated_by`.

Trial B compiled these repository and entity files (they are not in the error list). The 7.0 guide’s removed annotations are unused. Whether Hibernate 7.4 still accepts these constructor expressions and the array mapping at runtime was not tested. The Hibernate 7.2 query-language guide still documents constructor expressions (`select new ...`).

### Spring AI (bankrecon)

Commands:

```
rg -l 'org\.springframework\.ai' src --glob '*.java'
rg -c 'org\.springframework\.ai' src --glob '*.java'
```

Two files, 18 import occurrences: `BankReconAiConfig.java` and `SpringAiStructuredLlmClient.java`. No `PgVectorStore`, `BeanOutputConverter`, or `.entity()` structured-output call. Mapping JSON is parsed by `MappingResponseParser` after `ChatClient.call().chatResponse()`.

| Use in 1.0.0 | 1.1.8 (Boot 3.5) | 2.0.1 (Boot 4.1) |
| --- | --- | --- |
| `OllamaOptions` for chat and embeddings | Split. Jar 1.1.8 has `OllamaChatOptions` and `OllamaEmbeddingOptions` in `org.springframework.ai.ollama.api`. `OllamaOptions` is gone. `defaultOptions(...)` on `OllamaChatModel.Builder` takes `OllamaChatOptions`. | Same split. `numPredict`, `temperature`, `model` remain on `OllamaChatOptions.Builder`. |
| `OllamaApi.builder().baseUrl().restClientBuilder()` | `OllamaApi` is still in the 1.1.8 jar (Trial A did not report it missing). | `OllamaApi` still resolves in 2.0.1 (Trial B did not report it missing). |
| `OllamaChatModel.builder()` / `OllamaEmbeddingModel.builder()` | Builders remain. | Builders remain. 2.0 removes the in-model tool loop; this code does not register tools. |
| `OpenAiApi.builder().baseUrl().apiKey().restClientBuilder()` | Package `org.springframework.ai.openai.api` still exists in 1.1.8 (`OpenAiApi` is in that jar). | Package is absent from `spring-ai-openai` 2.0.1. `OpenAiChatModel.Builder` takes `openAiClient(com.openai.client.OpenAIClient)` and `httpClientBuilderCustomizer(...)`, not `openAiApi` / `RestClient`. |
| `OpenAiChatModel.builder().openAiApi().defaultOptions()` | Still the 1.0 shape in 1.1.8 (not reported by Trial A). | `options(OpenAiChatOptions)` plus `openAiClient`. `defaultOptions` is not on the 2.0.1 builder. |
| `new OpenAiEmbeddingModel(api, MetadataMode, options)` | Constructor still matches 1.0 in the sense Trial A did not fail it. | 2.0.1 constructors take `OpenAIClient` or options, not `OpenAiApi`. |
| `OpenAiChatOptions.builder().model().temperature().maxTokens()` | Unchanged at compile in Trial A. | `OpenAiChatOptions` still exists. 2.0 upgrade notes: options are immutable; `copy()` is removed; this code only uses the builder. |
| `ChatClient.builder(model).prompt().user().options(built).call().chatResponse()` | Call shape not reported broken in Trial A. | Upgrade notes: `.options()` / `.defaultOptions()` take a `ChatOptions.Builder`, not a built instance. Trial B did not emit a separate error for this, because `OllamaOptions` failed first. `getResult().getOutput().getText()` and `Usage.getPromptTokens()` were not type-checked on their own. |
| `EmbeddingModel.embed(String)` | Not reported broken. | `embed(Document)` is public on `OpenAiEmbeddingModel` 2.0.1. `embed(String)` is inherited; not separately confirmed. |
| `PgVectorStore` | Not referenced in Java. | Starter still in the 2.0.1 BOM. Auto-config stays excluded. |
| Structured output | Hand-rolled parse, not `BeanOutputConverter`. | The 2.0 `BeanOutputConverter` / JSON-schema changes do not apply to this call path. |
| `MappingJackson2HttpMessageConverter` on the Ollama `RestClient` | Still the Jackson 2 converter. | Class still exists in Spring Web 7.0.9. The Ollama interceptor that forces `"stream":false` and rewrites `application/octet-stream` is local code and is the fragile part of the 2.0 move. |

`application.yml` sets `spring.ai.model.chat=none` and `embedding=none`, and excludes `PgVectorStoreAutoConfiguration`, because `BankReconAiConfig` builds clients from `general_config`.

Size: **S** (2 files). Risk: **high**.

### Spring Modulith

Commands:

```
rg -l '@org.springframework.modulith.ApplicationModule' src --glob 'package-info.java'
rg -n 'NamedInterface|ApplicationModules|@ApplicationModuleListener|@ApplicationModuleTest' src --glob '*.java'
```

| | Files | Occurrences | Size | Risk |
| --- | ---: | ---: | --- | --- |
| `package-info.java` | 9 | — | S | low |
| `@ApplicationModule` | 8 | 8 | S | low |
| `@NamedInterface("dto")` | 1 (`contracts/dto`) | 1 | S | low |
| `ModularityTest` (`ApplicationModules.of`) | 1 | 3 calls | S | low |
| `@ApplicationModuleListener` | 1 live (`BudgetingEventListener`), 1 commented | 1 | S | low |
| `@ApplicationModuleTest` | 1 (`PeopleModuleIntegrationTest`) | 1 | S | low |
| `event_publication` in Flyway SQL | 0 | 0 | S | low |

Modules with `package-info`: reports, revenue (OPEN), application (OPEN), budgeting (OPEN), system (OPEN), contracts, bankrecon, expenses (OPEN), plus the contracts dto named interface. `people`, `customer`, and `general` are implicit modules (no `package-info`).

Trial B compiled the `package-info` files and, on the continue-on-error pass, did not report `ModularityTest`. Modulith 2.0 removes `@ApplicationEventListener` (the deprecated one). This code uses `@ApplicationModuleListener`. The POM has `spring-modulith-events-api` and not the JDBC registry starter. Modulith 2.0’s event-publication schema change matters when that registry is on the classpath. `spring.modulith.runtime.flyway-enabled` defaults to `false` (Modulith appendix), so module-specific Flyway folders stay off.

### Flyway

| | Files | Occurrences | Size | Risk |
| --- | ---: | ---: | --- | --- |
| `flyway-core` direct dependency | 1 POM | 1 | S | low |
| `spring.flyway` in `application.yml` | 1 | `enabled`, `locations: classpath:db/migration`, `baseline-on-migrate: false` | S | low |
| Module-specific migration config | 0 | 0 | S | low |

Boot 4.0 requires `spring-boot-starter-flyway` or auto-config is not on the classpath. Flyway goes 10.10.0 → 11.7.2 on 3.5.16 → 12.4.0 on 4.1.1. The Postgres database module is already a separate dependency, which matches the Flyway 10 split. One migration location, no per-module folders.

### Tests

Commands:

```
find src/test/java -name '*Test.java' -o -name '*Tests.java'
rg -l '@SpringBootTest|@DataJpaTest|@WebMvcTest|@MockBean|PostgreSQLContainer' src/test --glob '*.java'
```

| | Files | Occurrences | Size | Risk |
| --- | ---: | ---: | --- | --- |
| Test classes (`*Test.java` / `*Tests.java`) | 43 | — | M | medium |
| Java files under `src/test` (compiler saw 44) | 44 | — | M | medium |
| `@SpringBootTest` | 17 | — | M | medium |
| `@WebMvcTest` | 7 | — | S | low |
| `@DataJpaTest` | 0 | 0 | S | low |
| `@MockBean` | 10 | 12 | M | high for Boot 4 compile |
| `PostgreSQLContainer` + `@ServiceConnection` | 18 | 18 containers | M | low at compile on TC 2.0.5 |
| `@AutoConfigureMockMvc` | present on MVC tests | — | S | low |

Boot 4.0 also stops providing MockMvc from `@SpringBootTest` alone. Tests that use `MockMvc` need `@AutoConfigureMockMvc`. The MVC tests already use `@WebMvcTest` or their own setup; this was not a Trial B error because test sources did not compile (see below).

`@MockBean` is the test change Boot 4 forces. Testcontainers 2.0.5 still ships the old `org.testcontainers.containers.PostgreSQLContainer` with the `String` constructor these tests use (`new PostgreSQLContainer<>("pgvector/pgvector:pg16")`). That class is `@Deprecated`. The new class is `org.testcontainers.postgresql.PostgreSQLContainer` and has no no-arg constructor.

## Trial results

Worktrees (removed after the run):

- `/Users/vnatu/projects/cognologix/fpa-upgrade-trial-35` at `07d9d03`
- `/Users/vnatu/projects/cognologix/fpa-upgrade-trial-41` at `07d9d03`

Command ( `-q` omitted so deprecation warnings would show; the compiler line is `javac [debug deprecation parameters release 21]` ):

```
mvn -DskipTests -Dmaven.compiler.showDeprecation=true -Dmaven.compiler.showWarnings=true clean test-compile
```

No tests, no application start, no database. `test-compile` stops when main sources fail, so test sources were not compiled by this command.

### Trial A — Boot 3.5.16, Spring AI 1.1.8, Modulith 1.4.13

POM-only: parent `3.5.16`, `spring-modulith.version` `1.4.13`, `spring-ai-bom` `1.1.8`. Spring AI 1.1.8’s root POM pins Boot **3.5.15**. Modulith 1.4 is the Boot 3.5 line.

| | |
| --- | --- |
| Compile errors | **2** (`[INFO] 2 errors`) |
| Deprecation warnings | **0** |
| Test sources compiled | no |

Both errors are the same cause.

| Cause | Errors | Example |
| --- | ---: | --- |
| `OllamaOptions` removed from `org.springframework.ai.ollama.api` | 2 | `BankReconAiConfig.java:11` (the other is `SpringAiStructuredLlmClient.java:10`) |

Jar check: `spring-ai-ollama` 1.0.0 contains `OllamaOptions`. 1.1.8 contains `OllamaChatOptions` and `OllamaEmbeddingOptions` instead. Chat call sites should use `OllamaChatOptions`. The embedding builder should use `OllamaEmbeddingOptions`.

### Trial B — Boot 4.1.1, Spring AI 2.0.1, Modulith 2.1.1

POM-only mechanical changes: parent `4.1.1`, Modulith `2.1.1`, Spring AI BOM `2.0.1`, `spring-boot-starter-web` → `spring-boot-starter-webmvc`, `flyway-core` → `spring-boot-starter-flyway` (Postgres module kept), `junit-jupiter` → `testcontainers-junit-jupiter`, `postgresql` test artifact → `testcontainers-postgresql`. springdoc left at 2.5.0. Application code not edited.

Dependencies resolved. Main compile then failed.

| | |
| --- | --- |
| Compile errors | **4** (`[INFO] 4 errors`) |
| Deprecation warnings | **0** |
| Test sources compiled | no |

| Cause | Errors | Example |
| --- | ---: | --- |
| `OllamaOptions` removed (same split as 1.1) | 2 | `SpringAiStructuredLlmClient.java:10` |
| `org.springframework.ai.openai.api` removed; `OpenAiApi` is gone | 2 | `BankReconAiConfig.java:16` (`package ...openai.api does not exist`, and the `OpenAiApi` return type at line 168) |

`OpenAiChatModel`, `OpenAiChatOptions`, `OllamaApi`, `OllamaChatModel`, `SecurityConfig`, JPA repositories, and `MappingJackson2HttpMessageConverter` were not in the error list. The 2.0.1 `OpenAiChatModel.Builder` methods are `openAiClient(OpenAIClient)`, `options(OpenAiChatOptions)`, and `httpClientBuilderCustomizer(...)`.

A second `test-compile` with `-Dmaven.compiler.failOnError=false` was run only to see whether test diagnostics appeared. It printed about 100 further messages. Almost all of them are “package `com.cognologix.fpa....` does not exist” because main class files for the broken compilation were not usable. Those are not upgrade findings. One message is a real Boot 4 break: `BankReconServiceIntegrationTest.java` — `package org.springframework.boot.test.mock.mockito does not exist` (`@MockBean`). That matches the Boot 4.0 migration guide.

## Recommended order

Boot 4.0’s own guide says to move to the latest 3.5.x first and clear deprecations before 4.0. 4.1.1 then removes what 4.0 deprecated.

**Step 1 — 3.3.2 → 3.5.16.** Parent 3.5.16, Spring AI BOM 1.1.8, Modulith 1.4.13. Replace `OllamaOptions` with `OllamaChatOptions` or `OllamaEmbeddingOptions` in the two bankrecon files. Re-run the suite.

Effort: **2–4 days**.

Assumptions: one person who already works in this repo; the code change is the options split plus a manual pass of the Ollama and oMLX call; pre-existing Testcontainers failures (see below) are not counted as upgrade work; no prompt-quality study, only a smoke call.

**Step 2 — 3.5.16 → 4.1.1.** Parent 4.1.1, Spring AI 2.0.1, Modulith 2.1.1, the starter and Testcontainers renames from Trial B, springdoc 3.1.1, `@MockBean` → `@MockitoBean` on the 10 test classes. Rebuild `OpenAiApi` / `RestClient` construction on `OpenAIClient` and `OpenAiHttpClientBuilderCustomizer`, and pass option builders into `ChatClient.options()`. Smoke the two `select new` queries, the `integer[]` column, and the payroll generated columns against Postgres.

Effort: **5–8 days** after step 1 is compiling and the suite is understood.

Assumptions: same person; the Ollama non-streaming interceptor is rewritten against whatever HTTP hook 2.0.1 still allows, and that rewrite is the long pole; Hibernate 7 does not force a Flyway rewrite (`ddl-auto: none`); JJWT stays on Jackson 2; a full LLM quality regression is out of scope.

Combined: **about 7–12 person-days**.

## Top 5 risks

1. **oMLX / OpenAI client rewrite.** 2.0.1 deletes `OpenAiApi` and the `RestClient` builder this config uses for timeouts, body capture, and the API key. The upgrade notes say OpenAI builders stay intact. The 2.0.1 jar does not contain `OpenAiApi`. The custom client is the highest functional risk.
2. **Ollama non-streaming interceptor.** `NonStreamingChatInterceptor` patches `/api/chat` JSON so Spring AI 1.0 can parse a non-stream response. 1.1 and 2.0 both drop `OllamaOptions`, and 2.0 changes how chat calls are made. A compile fix that only renames the options class can still break ledger mapping at runtime.
3. **`@MockBean` removal.** 10 test classes, 12 annotations. Boot 4 deletes the package. The strict trial never compiled tests; the continue-on-error pass showed the missing package on `BankReconServiceIntegrationTest`.
4. **Hibernate 6.5 → 7.4.** Compile of the repositories succeeded. Runtime of `select new`, `SqlTypes.ARRAY`, and `GENERATED ALWAYS AS ... STORED` columns was not run. `ddl-auto: none` avoids Hibernate rewriting the schema.
5. **springdoc 2.5.0 left on a Boot 4 classpath.** No Java compile failure. Swagger UI is on the public security matcher, so a bad starter can fail startup. Move to 3.1.1 in the same step as Boot 4.

## Unverified

- Spring Security 7 servlet migration page (`.../7.0/migration/servlet.html` and `.../migration/servlet.html`) returned 404. The migration index was read. `SecurityConfig` compiled on Security 7.1.1, which covers removed-method breaks and does not cover runtime filter behavior.
- springdoc 3.1.1 release notes do not say “Boot 4.1”. They do depend on Boot 4 module artifacts. 3.0.0 is explicitly Boot 4.0.0.
- Hibernate 7.4 migration guide was not read in full. The 7.0 guide and the 7.2 query-language page were. Generated-column and constructor-expression behavior on 7.4.5 is a compile pass only.
- `ChatClient.options(Builder)` was not a separate compiler error. The requirement is from the Spring AI 2.0 upgrade notes.
- Whether Boot 4 still exposes an `ObjectMapper` bean for the five tests. The class is on the classpath via JJWT; the auto-configured bean type in Boot 4 is `JsonMapper`.
- Modulith 2.0 event-registry schema. No `event_publication` migration and no JDBC registry starter. Not started.
- `spring-boot-properties-migrator` was not run, because that means starting the application.
- Existing Testcontainers **runtime** failures. This trial did not execute tests.

## Existing Testcontainers failures

The commit message on `07d9d03` says there are Testcontainers issues. This trial cannot show what they do at runtime.

What the compile and the 2.0.5 jar do show:

- Trial B resolved `testcontainers-junit-jupiter` and `testcontainers-postgresql` 2.0.5.
- `org.testcontainers.containers.PostgreSQLContainer` is still in that jar, still has `PostgreSQLContainer(String)`, and is `@Deprecated`. The 18 tests use that constructor with `pgvector/pgvector:pg16`.
- The strict `test-compile` never reached those files, so no new deprecation warning was counted. Nothing in the failed main compile indicates the upgrade makes those tests fail to compile.
- The upgrade does not fix a runtime container failure. Testcontainers moves from 1.19.8 to 2.0.5 only on the Boot 4.1 step. The 3.5.16 step moves it to 1.21.4, which keeps the old artifact names.

## Summary

Overall size: **medium**. Two production files fail to compile; the rest of the 356 main sources compiled on Boot 4.1.1 once the POM was adjusted. The follow-on work is the OpenAI client rewrite, ten `@MockBean` tests, springdoc 3.x, and a Hibernate 7 smoke test.

Biggest risk: Spring AI 2.0 removes `OpenAiApi` and the `RestClient` hook the bank-reconciliation client uses for oMLX and Ollama.

First step: move to Boot 3.5.16 with Spring AI 1.1.8 and Modulith 1.4.13, and replace `OllamaOptions` with `OllamaChatOptions` / `OllamaEmbeddingOptions`.
