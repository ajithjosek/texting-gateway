# Testing Strategy — Enterprise Texting Gateway
**Pyramid:** unit (fast, mocked) → API (MockMvc + H2) → UI (Playwright vs real served page) → integration (`*IT`, needs compose stack, excluded by default).

## Layers
| Layer | Where | Runs on | Command |
|---|---|---|---|
| Unit | `src/test/**/consent/*UnitTest`, `messaging/*Test` (Mockito, no Spring) | every build | `mvn test -Dtest='*UnitTest,*ControllerTest,TwilioSenderTest'` |
| API | `src/test/**/api/*ApiTest` (`@SpringBootTest` + MockMvc + H2) | every build | `mvn test` |
| UI mirror | `src/test/**/ui/FrontendStaticTest` (asserts `frontend/index.html` markers, no browser) | every build | `mvn test` |
| UI browser | `frontend/tests/*.spec.ts` (Playwright, `file://` placeholder; `ETG_UI_URL=http://localhost:3000` vs compose) | CI + pre-merge | `npm test --prefix frontend` |
| Integration | `src/test/**/*IT.java` (Failsafe, needs `docker compose up`) | nightly / pre-release | `mvn verify -DskipITs=false` (to be added with first IT) |

## Conventions for new stories
- Every story ships: 1+ unit test for the rule (e.g. consent gate, quiet hours), 1 API test for the contract (status codes + TwiML/JSON shape), UI spec update if it changes rendered output.
- Name API tests `<Area>ApiTest`; browser specs `<area>.spec.ts` mirroring `FrontendStaticTest` markers.
- Twilio webhooks: always test form-encoded (`MediaType.APPLICATION_FORM_URLENCODED`) + assert TwiML contains the keyword reply; consent side-effects asserted via a follow-up gated send.
- Coverage gate: JaCoCo 60% line minimum on `mvn verify` (raise to 75% at GA).

## Local runs
```bash
mvn test                                   # unit + API + UI mirror (~60s)
npm test --prefix frontend                 # browser UI (needs: npm install --prefix frontend + browsers once)
ETG_UI_URL=http://localhost:3000 npm test --prefix frontend   # vs compose stack
mvn verify                                 # full gate incl. JaCoCo check
```
