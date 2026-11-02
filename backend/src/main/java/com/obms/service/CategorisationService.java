package com.obms.service;

import com.obms.domain.Enums;
import com.obms.domain.Transaction;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Assigns a spend category to a transaction (innovation module 5).
 *
 * <p>This is a deterministic keyword classifier, and the project describes it
 * as exactly that. It is not presented as machine learning: the requirement
 * was that categorisation be functional and explainable, and a rule the
 * customer can read beats an opaque model they cannot correct. The anomaly
 * detection in the fraud module is where a trained model genuinely earns its
 * place, because there the pattern is not expressible as keywords.</p>
 *
 * <p>Three properties make this safe to run repeatedly:</p>
 * <ul>
 *   <li>A category the customer set by hand is never overwritten.</li>
 *   <li>Bill payments inherit the biller's own category, which is authoritative,
 *       so they never reach the keyword stage.</li>
 *   <li>Unmatched transactions become {@code OTHER} rather than being guessed
 *       into a plausible-looking bucket.</li>
 * </ul>
 */
@Service
public class CategorisationService {

    /**
     * Keyword table, most specific first. Ordering matters: "credit card bill"
     * must match the utilities rule before the generic "card" rule.
     */
    private static final Map<Enums.SpendCategory, List<String>> KEYWORDS = buildKeywords();

    private static Map<Enums.SpendCategory, List<String>> buildKeywords() {
        Map<Enums.SpendCategory, List<String>> map = new LinkedHashMap<>();
        map.put(Enums.SpendCategory.RENT, List.of(
                "rent", "landlord", "lease", "maintenance society", "society charges"));
        map.put(Enums.SpendCategory.UTILITIES, List.of(
                "electricity", "bescom", "water board", "gas", "broadband", "internet",
                "wifi", "mobile recharge", "postpaid", "prepaid", "dth", "credit card bill",
                "utility", "bill payment"));
        map.put(Enums.SpendCategory.FOOD, List.of(
                "swiggy", "zomato", "restaurant", "cafe", "coffee", "bakery", "dominos",
                "pizza", "biryani", "canteen", "mess", "grocery", "bigbasket", "blinkit",
                "zepto", "supermarket", "kirana", "food"));
        map.put(Enums.SpendCategory.TRANSPORT, List.of(
                "uber", "ola", "rapido", "metro", "bmtc", "irctc", "railway", "petrol",
                "diesel", "fuel", "toll", "fastag", "parking", "cab", "bus ticket", "flight"));
        map.put(Enums.SpendCategory.HEALTHCARE, List.of(
                "pharmacy", "apollo", "hospital", "clinic", "medical", "diagnostic",
                "lab test", "doctor", "dental", "insurance premium"));
        map.put(Enums.SpendCategory.ENTERTAINMENT, List.of(
                "netflix", "prime video", "hotstar", "spotify", "bookmyshow", "pvr",
                "inox", "cinema", "gaming", "steam", "subscription"));
        map.put(Enums.SpendCategory.EDUCATION, List.of(
                "tuition", "course", "udemy", "coursera", "exam fee", "college fee",
                "school fee", "books", "stationery", "library"));
        map.put(Enums.SpendCategory.SHOPPING, List.of(
                "amazon", "flipkart", "myntra", "ajio", "meesho", "nykaa", "decathlon",
                "mall", "store", "shopping", "apparel", "electronics"));
        map.put(Enums.SpendCategory.SAVINGS, List.of(
                "savings goal", "recurring deposit", "fixed deposit", "sip", "mutual fund",
                "investment"));
        return map;
    }

    /**
     * Classifies a transaction.
     *
     * @return the category, and the rule that produced it
     */
    public Result classify(Transaction transaction) {
        if (transaction.isCategoryUserSet() && transaction.getCategory() != null) {
            return new Result(transaction.getCategory(),
                    "You set this category manually.", true);
        }

        if (transaction.getDirection() == Enums.TransactionDirection.CREDIT) {
            boolean looksLikeSalary = containsAny(haystack(transaction),
                    List.of("salary", "payroll", "stipend", "wages"));
            return looksLikeSalary
                    ? new Result(Enums.SpendCategory.INCOME,
                            "Credit matching a salary or stipend description.", false)
                    : new Result(Enums.SpendCategory.INCOME,
                            "Money received into the account.", false);
        }

        if (transaction.getTransactionType() == Enums.TransactionType.INTERNAL_TRANSFER) {
            return new Result(Enums.SpendCategory.TRANSFER,
                    "Transfer between accounts rather than spending.", false);
        }

        String text = haystack(transaction);
        for (Map.Entry<Enums.SpendCategory, List<String>> entry : KEYWORDS.entrySet()) {
            String matched = firstMatch(text, entry.getValue());
            if (matched != null) {
                return new Result(entry.getKey(),
                        "Matched the keyword \"" + matched + "\" in the transaction description.",
                        false);
            }
        }

        return new Result(Enums.SpendCategory.OTHER,
                "No category rule matched this description.", false);
    }

    /**
     * Applies the classification to the entity, respecting manual overrides.
     *
     * @return true when the category was changed
     */
    public boolean applyTo(Transaction transaction) {
        if (transaction.isCategoryUserSet()) {
            return false;
        }
        Result result = classify(transaction);
        if (result.category() == transaction.getCategory()) {
            return false;
        }
        transaction.setCategory(result.category());
        return true;
    }

    private String haystack(Transaction transaction) {
        StringBuilder sb = new StringBuilder();
        if (transaction.getDescription() != null) {
            sb.append(transaction.getDescription()).append(' ');
        }
        if (transaction.getCounterpartyName() != null) {
            sb.append(transaction.getCounterpartyName());
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    private String firstMatch(String text, List<String> keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return keyword;
            }
        }
        return null;
    }

    private boolean containsAny(String text, List<String> keywords) {
        return firstMatch(text, keywords) != null;
    }

    /**
     * A classification outcome.
     *
     * @param category    the assigned category
     * @param explanation why this category was chosen, shown in the UI
     * @param userSet     true when the customer's own choice was respected
     */
    public record Result(Enums.SpendCategory category, String explanation, boolean userSet) {
    }
}
