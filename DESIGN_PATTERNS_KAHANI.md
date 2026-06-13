# 🎬 Design Patterns ki Kahani — "Ek Idea Ka Safar"

> Yeh file Roman Urdu mein hai taake presentation se pehle aaram se yaad ho jaye:
> **kaunsa design pattern kahan use ho raha hai, kya kaam kar raha hai, aur code mein kahan milega.**
> Har pattern ke saath **file + line reference** diya hai taake khud ja kar check kar sako.

---

## Kahani: ek user ka *idea* ek software company ke andar safar karta hai

Socho yeh app ek **software company** hai. Ek customer (user) sirf ek line likhta hai — *"food delivery app banao"* — aur yeh idea company ke andar 5 experts (agents) ke paas se guzar kar ek poori planning report ban jaata hai. Is safar mein har jagah koi na koi design pattern apna kaam karta hai.

---

## 🏢 PART 1 — Company subah khulti hai (tayari ke patterns)

Jab app start hoti hai, **Spring** ek dafa employees aur tools bana kar rakh leta hai — baar baar naye nahi banata.

### 1) Singleton — *"Ek hi rahega, sab share karenge"*
**Kya kaam:** Gemini AI client sirf **ek dafa** banta hai aur poori app use share karti hai. Har request pe naya connection banana memory aur paisa waste karta. Isliye ek hi shared instance jo saari API keys manage karta hai.
**Code:** `config/LangChain4jConfig.java` → `geminiKeyServices()` bean + `GeminiKeyServices` class (line 57–123). Yeh Spring singleton hai.
⚠️ Spec mein iska naam `GeminiConnectionManager` tha — wo class code mein nahi hai, Spring singleton se kaam hota hai.

### 2) Factory Method — *"Cheezein banane ka kaam factory ko do"*
**Kya kaam:** Hum khud `new ...Agent()` nahi likhte. Spring + ek `@Bean` method khud objects bana kar deta hai. Object banane ki tension use karne wale code se alag ho jaati hai.
**Code:** `config/LangChain4jConfig.java` → `geminiKeyServices(...)` ke andar `new OpenRouterAiServiceImpl(model)` (line 81–90).
⚠️ Spec mein `AgentFactory` class maangi thi — wo nahi hai. Spring DI hi factory ka kaam karta hai. (Dekho `CentralOrchestrator.java` line 51 ka comment.)

### 3) Decorator — *"Purani cheez ko wrap kar ke extra powers do"*
**Kya kaam:** AI service ko ek doosri service *lapet* leti hai jo usmein **caching + API key rotation + token budget** add kar deti hai — bina asli service ko change kiye. Agents ko bas `chat()` call karna hai, baaki sab free mein mil jaata hai.
**Code:** `tokenmanagement/CachingAiService.java` → `implements AiService`, andar `chatWithRotation()` (line 143) jo keys badal badal kar try karta hai.

---

## 🧠 PART 2 — Customer idea deta hai (coordination ke patterns)

User likhta hai *"food delivery app banao"*. Yeh request seedha **manager** ke paas jaati hai, kisi employee ke paas direct nahi.

### 4) Mediator — *"Sab manager ke through baat karenge, aapas mein nahi"*
**Kya kaam:** `CentralOrchestrator` manager hai. Agents **ek doosre ko call nahi karte**. Har agent apna kaam kar ke manager ko result deta hai, manager decide karta hai agla kaun chalega. Isse agents aapas mein uljhe (tightly coupled) nahi hote.
**Code:** `core/CentralOrchestrator.java` → `runPipeline()` (line 98). Poora loop manager chalata hai.

### 5) Chain of Responsibility — *"Line mein khade experts, ek ke baad ek"*
**Kya kaam:** 5 agents tay shuda order mein chalte hain: **Requirement → Business → Database → Planner → Risk**. Har agent shared data (`ProjectState`) leta hai, apna hissa add karta hai, aur agle ko diya jaata hai. Agar koi fail ho to chain ruk jaati hai.
**Code:** `core/Agent.java` (sab agents `Agent<O>` implement karte hain) + `core/CentralOrchestrator.java` → `tasks` list (line 109–115) aur `for (AgentTask task : tasks)` loop (line 118).
⚠️ Agent khud agle ko nahi bhejta — **manager bhejta hai**. Isliye yeh "mediated chain" hai (Chain + Mediator mil kar).

---

## 🔧 PART 3 — Har expert kaam kaise karta hai

Manager kaam karwana chahta hai, lekin nahi jaanta expert *andar se kaise* kaam karta hai.

### 6) Bridge — *"'Kaam kya hai' aur 'kaam kaise hoga' — dono alag"*
**Kya kaam:** Manager sirf `AgentTask` (kaam ka definition) jaanta hai. Aaj kaam AI se hota hai (`LlmAgentTask`), kal rule-based ho sakta hai — manager ka code change nahi hoga.
**Code:** `infrastructure/AgentTask.java` (abstraction) + `infrastructure/LlmAgentTask.java` → `execute()` (line 53).

### 7) Adapter — *"Bahar ki library ko apni clean shakal do"*
**Kya kaam:** Agents Gemini library ko direct nahi chhoote. Beech mein ek saaf interface `AiService.chat()` hai. Library badal jaye (OpenRouter → Gemini) to bhi agents ka code waisa ka waisa rehta hai.
**Code:** `service/AiService.java` (interface) + `service/OpenRouterAiServiceImpl.java` → `chat()` (line 60) jahan asli LangChain4j call hoti hai.

---

## 📢 PART 4 — Beech mein khabrein chalti rehti hain

### 8) Observer — *"Kuch hua to sab listeners ko khabar do"*
**Kya kaam:** Jab koi agent kuch karta hai (jaise *"Designing Schema…"*), to `EventLogger` event publish karta hai. Jo bhi sun raha ho use khabar mil jaati hai.
**Code:** `observer/EventLogger.java` → `publish()` (line 72) + `PipelineEventListener` interface (line 113).
⚠️ Honest baat: abhi koi listener register nahi hua, isliye yeh mostly **logging** ke liye chal raha hai; live UI updates seedha SSE se jaate hain (`CentralOrchestrator.emit()` line 246).

---

## 🛡️ PART 5 — Database expert ka kaam (yahan kayi patterns ek saath)

Database Architect aata hai — yeh **3rd** expert hai. Pehle check karta hai upar wale 2 experts ne kaam diya ya nahi, phir AI se schema banwata hai, validate karta hai, aur ERD diagram **khud Java mein** banata hai.
**Code:** `agents/database/DatabaseArchitectAgent.java` → `execute()` (line 106).

### 9) Null Object — *"`null` mat do, ek khaali-safe cheez do"*
**Kya kaam:** Agar AI fail ho jaye, to agent `null` return nahi karta (jo crash kar deta). Balke ek **khaali `DatabaseContext`** (zero tables) deta hai. Manager dekhta hai "tables khaali hain" → chain gracefully ruk jaati hai, crash nahi hota.
**Code:** `agents/database/DatabaseArchitectAgent.java` → `buildFailure()` (line 288) jo `new DatabaseContext(List.of(), List.of(), "")` banata hai.
Aur `core/CentralOrchestrator.java` → `isNullObject()` (line 211) jo check karta hai `tables.isEmpty()`.
⚠️ Spec mein iska naam `EmptyReportSection` tha — wo class nahi, empty context records use hote hain.

---

## 🌳 PART 6 — Project plan ek darakht (tree) ki tarah

Planner expert project ko **phases aur tasks** mein todta hai — jaise ek darakht jiski shaakhon mein patte (tasks) lage hon.

### 10) Composite — *"Ek single task aur poora phase — dono ko ek jaisa treat karo"*
**Kya kaam:** `ProjectNode` = phase (jiske andar tasks ho sakte hain), `TaskLeaf` = single task. "Saare tasks gino" wala code dono pe ek jaisa chalta hai (recursion se).
**Code:** `context/GanttContext.java` → `ProjectComponent` interface (line 64), `ProjectNode` (line 90), `TaskLeaf` (line 107).

---

## 🧱 PART 7 — Aakhir mein report tayar hoti hai

Jab saare 5 experts ka kaam ho jaata hai, manager ek aur banda bulata hai jo **thoda thoda kar ke** final report jorta hai.

### 11) Builder — *"Bara object step-by-step banao"*
**Kya kaam:** `ProjectReportBuilder` har section alag method se jorta hai — `withRequirements()`, `withDatabaseDesign()`… aur aakhir mein `build()` poori Markdown report de deta hai. Agar koi section khaali ho to use skip kar deta hai.
**Code:** `core/ProjectReportBuilder.java` → `withHeader()...build()` (line 52–229). Manager ise use karta hai `CentralOrchestrator.java` line 143 pe.

---

## 🧠 Yaad rakhne ka aasan formula (kahani ka nichod)

> **Company khulti hai** → Singleton (ek AI client), Factory (Spring objects banata hai), Decorator (caching wrap).
> **Customer idea deta hai** → Mediator (manager), Chain (5 experts line mein).
> **Experts kaam karte hain** → Bridge (kaam vs tareeqa), Adapter (library ka clean wrapper).
> **Khabrein** → Observer (event logger).
> **Kuch bigde** → Null Object (khaali-safe result).
> **Plan** → Composite (phase/task tree).
> **Report** → Builder (step-by-step).

---

## 📋 Ek nazar mein table (code dhoondhne ke liye)

| # | Pattern | Aasan jumla | File | Kya dekhna hai |
|---|---|---|---|---|
| 1 | Singleton | Ek hi AI client, sab share karein | `config/LangChain4jConfig.java` | `GeminiKeyServices` (line 106) |
| 2 | Factory Method | Spring objects banata hai | `config/LangChain4jConfig.java` | `geminiKeyServices()` bean (line 57) |
| 3 | Decorator | AI service ko caching se wrap | `tokenmanagement/CachingAiService.java` | `chatWithRotation()` (line 143) |
| 4 | Mediator | Manager sab ko coordinate karta hai | `core/CentralOrchestrator.java` | `runPipeline()` (line 98) |
| 5 | Chain of Responsibility | 5 experts line mein | `core/Agent.java` + Orchestrator | `for(task : tasks)` (line 118) |
| 6 | Bridge | Kaam kya vs kaam kaise | `infrastructure/AgentTask.java` + `LlmAgentTask.java` | `execute()` (line 53) |
| 7 | Adapter | Library ka clean wrapper | `service/AiService.java` + `OpenRouterAiServiceImpl.java` | `chat()` (line 60) |
| 8 | Observer | Event hua to khabar do | `observer/EventLogger.java` | `publish()` (line 72) |
| 9 | Null Object | null ki jagah khaali-safe object | `agents/database/DatabaseArchitectAgent.java` | `buildFailure()` (line 288) |
| 10 | Composite | Phase/task ka tree | `context/GanttContext.java` | `ProjectComponent` (line 64) |
| 11 | Builder | Report step-by-step | `core/ProjectReportBuilder.java` | `build()` (line 227) |

---

## ⚠️ Presentation ke liye 3 honest baatein (taake examiner ke saamne na phanso)

1. **Factory Method** — Spec ne `AgentFactory` class maangi thi, wo code mein **nahi hai**. Spring DI use hua hai. Bolna: *"Humne Spring container aur @Bean factory method use kiya object banane ke liye."*
2. **Observer** — `EventLogger` ka poora system bana hua hai, lekin abhi koi listener **register nahi** hua, to filhaal yeh sirf logging karta hai; live UI seedha SSE se update hoti hai.
3. **Chain of Responsibility** — Agents ek doosre ko direct call **nahi** karte; manager (Mediator) forward karta hai. Yeh "mediated chain" hai.

---

*Yeh file source code se bani hai. File: `DESIGN_PATTERNS_KAHANI.md` — Last update: 2026-06-11.*
