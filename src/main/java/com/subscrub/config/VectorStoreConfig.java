package com.subscrub.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.List;
import java.util.Map;

@Configuration
public class VectorStoreConfig {

    private static final Logger log = LoggerFactory.getLogger(VectorStoreConfig.class);

    /**
     * Local ONNX embedding model — all-MiniLM-L6-v2 (22MB, 384-dim, CPU-only).
     * Downloads tokenizer + model from GitHub on first run and caches them.
     * No API key required.
     */
    @Bean
    public EmbeddingModel embeddingModel() throws Exception {
        TransformersEmbeddingModel model = new TransformersEmbeddingModel();
        model.afterPropertiesSet(); // downloads + initialises the ONNX model
        return model;
    }

    /**
     * Creates and populates the in-memory vector store with intent anchor examples.
     *
     * If the embedding API is unreachable at startup (e.g. no HF token / cold start),
     * the store is left empty and every query falls back to Gemini SQL generation.
     * No intent-router short-circuit will fire, but the app remains fully functional.
     */
    @Bean
    public SimpleVectorStore simpleVectorStore(EmbeddingModel embeddingModel) {
        SimpleVectorStore store = SimpleVectorStore.builder(embeddingModel).build();

        try {
            store.add(intentDocs("LIST_SUBSCRIPTIONS", List.of(
                "show all my subscriptions",
                "what am I paying for every month",
                "list all recurring payments and their cost",
                "which services am I subscribed to",
                "show my monthly subscription list",
                "what apps am I paying for",
                "my subscriptions",
                "what subs do I have",
                "active subscriptions list",
                "which services are still running",
                "how much is each subscription",
                "what's my most expensive subscription",
                "all my recurring charges",
                "show me what I'm subscribed to",
                "list my active plans",
                "what recurring payments do I have",
                "display all my memberships",
                "what services am I being charged for",
                "show current subscriptions and prices",
                "what do I pay for regularly"
            )));

            store.add(intentDocs("PRICE_CHANGES", List.of(
                "which payments went up this year",
                "did any subscription increase in price",
                "show me price hikes in my subscriptions",
                "which recurring charges became more expensive",
                "Netflix raised their price",
                "compare this year vs last year for my subscriptions",
                "did anything get cheaper",
                "which subscription reduced in cost",
                "how much did Netflix go up by",
                "price changes in last 6 months",
                "what changed since January",
                "any subscription price hikes",
                "who charged me more this year",
                "subscriptions that increased their price",
                "show me cost increases",
                "which services raised their rates",
                "any price drops in my subscriptions",
                "compare old price vs new price",
                "what got more expensive recently",
                "did my subscription costs change"
            )));

            store.add(intentDocs("UPCOMING_DEBITS", List.of(
                "what will be auto debited in the next 10 days",
                "upcoming payments this week",
                "what subscriptions renew soon",
                "show me charges coming up",
                "what gets deducted next week",
                "next auto debit dates",
                "what's due tomorrow",
                "payments coming this month",
                "when is Netflix due",
                "next renewal date for my subscriptions",
                "what bills are coming up",
                "upcoming auto pay schedule",
                "what will be charged soon",
                "show me my next billing dates",
                "when do my subscriptions renew",
                "what payments are due in 30 days",
                "scheduled debits this week",
                "which subs are renewing soon",
                "what's getting auto charged next",
                "show upcoming subscription charges"
            )));

            store.add(intentDocs("SPEND_SUMMARY", List.of(
                "how much do I spend on subscriptions per month",
                "how much do I spend per month",
                "total subscription cost this month",
                "subscription spend this month",
                "how much am I spending monthly on apps",
                "summarize my subscription costs",
                "monthly subscription total",
                "what's my total monthly bill",
                "total recurring charges this month",
                "how much am I spending on OTT this month",
                "give me a spending summary",
                "how much am I losing to subscriptions",
                "what do I pay in total each month",
                "how much am I paying for subscriptions right now",
                "current month subscription spend",
                "what is my total subscription bill",
                "average monthly subscription expense",
                "how much do subscriptions cost me per year",
                "yearly subscription cost",
                "entertainment spending total"
            )));

            store.add(intentDocs("CANCEL_SIMULATION", List.of(
                "what if I cancel Netflix",
                "how much do I save if I cancel Spotify and gym",
                "simulate cancelling my OTT subscriptions",
                "what would I save by cancelling Disney plus",
                "if I stop paying for Netflix how much do I save",
                "cancel simulation for gym and Hotstar",
                "how can I reduce my subscription bills",
                "ways to save on subscriptions",
                "what happens if I drop Netflix",
                "remove Spotify how much would I save",
                "cancel all OTT subscriptions savings",
                "drop everything except Spotify",
                "which subscription should I cancel to save the most",
                "what if I cancel Netflix and Spotify",
                "savings if I unsubscribe from gym",
                "simulate removing LinkedIn Premium",
                "how much can I save by cancelling",
                "what if I stop all subscriptions",
                "estimate savings for cancelling Audible and iCloud",
                "help me cut subscription costs"
            )));

            log.info("Vector store populated with intent anchor documents (5 original intents)");

            // ── 8 new intents (10 anchors each = 80 more) ───────────────────

            // Compare / Summarize — month-over-month trends
            store.add(intentDocs("MONTHLY_TREND", List.of(
                "show my monthly spending trend",
                "compare month by month subscription costs",
                "how did my spend change over time",
                "spending trend over the past year",
                "month over month comparison",
                "compare this month with previous month",
                "compare it with last month",
                "how much did I spend each month",
                "monthly cost history",
                "is my spending going up or down"
            )));

            // Breakdown — category decomposition
            store.add(intentDocs("CATEGORY_BREAKDOWN", List.of(
                "which category costs me the most",
                "which category costs the most",
                "spending by category",
                "breakdown of subscription costs by category",
                "show the breakdown of my spending",
                "what makes up my total subscription bill",
                "how much goes to entertainment vs fitness",
                "category wise subscription cost",
                "split my subscriptions by type",
                "what percentage is entertainment",
                "what percentage goes to entertainment",
                "decompose my subscription spending",
                "breakdown of subscription spending by category"
            )));

            // Understand what changed — anomalies
            store.add(intentDocs("ANOMALY_LIST", List.of(
                "show unusual charges",
                "show unusual charges in my subscriptions",
                "any anomalies in my payments",
                "unexpected subscription charges",
                "which payments were abnormal",
                "flag suspicious charges",
                "weird charges on my subscriptions",
                "did any subscription charge differently",
                "show me irregular payments",
                "alert me about odd subscription amounts",
                "any surprise charges",
                "anomaly detection on my subscriptions",
                "detect unusual subscription activity"
            )));

            // Breakdown — top-N expensive
            store.add(intentDocs("MOST_EXPENSIVE", List.of(
                "what is my most expensive subscription",
                "which subscription costs the most",
                "top 5 priciest subscriptions",
                "my costliest services",
                "where is most of my money going",
                "highest recurring charge",
                "rank my subscriptions by price",
                "biggest subscription expense",
                "which service drains my wallet the most",
                "show me the most costly subscriptions"
            )));

            // Understand what changed — cancelled
            store.add(intentDocs("CANCELLED_SUBS", List.of(
                "show cancelled subscriptions",
                "what did I stop paying for",
                "which services did I cancel",
                "list my paused or cancelled plans",
                "subscriptions I no longer have",
                "previously cancelled services",
                "what subscriptions did I drop",
                "inactive subscriptions",
                "show me services I unsubscribed from",
                "which memberships did I end"
            )));

            // Summarize — recent transactions
            store.add(intentDocs("RECENT_TRANSACTIONS", List.of(
                "show my recent transactions",
                "last 20 payments",
                "recent subscription charges",
                "show recent debits",
                "what was charged recently",
                "my latest transactions",
                "recent payment history",
                "show me what was deducted recently",
                "last few subscription payments",
                "transaction history"
            )));

            // Conversational — user info
            store.add(intentDocs("USER_INFO", List.of(
                "what is my user name",
                "who am I",
                "show my profile",
                "my account details",
                "what is my email",
                "when did I join",
                "my user info",
                "show my account information",
                "what name is on my account",
                "my profile details"
            )));

            // Conversational — greetings and help
            store.add(intentDocs("GREETING", List.of(
                "hi",
                "hello",
                "hey there",
                "good morning",
                "help",
                "what can you do",
                "how does this work",
                "hi what can I ask",
                "hey help me out",
                "what questions can I ask"
            )));

            log.info("Vector store now has ~190 total anchor documents (13 intents)");
        } catch (Exception ex) {
            log.warn("Vector store population failed (HF API unreachable?) \u2014 " +
                     "all queries will use Gemini SQL fallback. Cause: {}", ex.getMessage());
        }

        return store;
    }

    private List<Document> intentDocs(String intent, List<String> examples) {
        return examples.stream()
            .map(text -> new Document(text, Map.of("intent", intent)))
            .toList();
    }
}
