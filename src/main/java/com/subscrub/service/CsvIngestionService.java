package com.subscrub.service;

import com.opencsv.CSVReader;
import com.subscrub.model.Transaction;
import com.subscrub.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses uploaded CSVs into transactions and triggers recurrence detection.
 * Expected CSV format: date, amount, merchant, [description], [channel]
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CsvIngestionService {

    private final TransactionRepository txRepo;
    private final RecurrenceDetector recurrenceDetector;

    /**
     * Ingests a CSV file of transactions for the given user.
     * After saving, automatically runs recurrence detection.
     *
     * @return number of transactions imported
     */
    public int ingest(MultipartFile file, String userId) {
        List<Transaction> batch = new ArrayList<>();

        try (CSVReader reader = new CSVReader(
                new InputStreamReader(file.getInputStream()))) {

            String[] headers = reader.readNext(); // skip header row
            if (headers == null) return 0;

            // Determine column indices from header
            int idxDate        = findIndex(headers, "date");
            int idxAmount      = findIndex(headers, "amount");
            int idxMerchant    = findIndex(headers, "merchant");
            int idxDescription = findIndex(headers, "description");
            int idxChannel     = findIndex(headers, "channel");
            int idxRaw         = findIndex(headers, "raw_entry");

            String[] row;
            while ((row = reader.readNext()) != null) {
                if (row.length == 0 || row[0].isBlank()) continue;
                try {
                    Transaction tx = buildTransaction(
                        row, userId, idxDate, idxAmount, idxMerchant,
                        idxDescription, idxChannel, idxRaw
                    );
                    batch.add(tx);
                } catch (Exception e) {
                    log.warn("Skipping malformed row: {} — {}", String.join(",", row), e.getMessage());
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse CSV: " + e.getMessage(), e);
        }

        txRepo.saveAll(batch);
        log.info("Imported {} transactions for user {}", batch.size(), userId);

        // Detect recurring subscriptions from the newly imported data
        recurrenceDetector.detect(userId);

        return batch.size();
    }

    private Transaction buildTransaction(
            String[] row, String userId,
            int idxDate, int idxAmount, int idxMerchant,
            int idxDescription, int idxChannel, int idxRaw) {

        Transaction tx = new Transaction();
        tx.setUserId(userId);
        tx.setDate(LocalDate.parse(safeGet(row, idxDate)));
        tx.setAmount(new BigDecimal(safeGet(row, idxAmount)));
        tx.setMerchant(safeGet(row, idxMerchant));
        tx.setDescription(safeGet(row, idxDescription));
        tx.setChannel(safeGet(row, idxChannel));
        tx.setRawEntry(safeGet(row, idxRaw));
        tx.setCurrency("INR");
        return tx;
    }

    private int findIndex(String[] headers, String name) {
        for (int i = 0; i < headers.length; i++) {
            if (headers[i].trim().equalsIgnoreCase(name)) return i;
        }
        return -1;
    }

    private String safeGet(String[] row, int idx) {
        if (idx < 0 || idx >= row.length) return null;
        String val = row[idx].trim();
        // Strip surrounding quotes that opencsv may leave
        if (val.startsWith("\"") && val.endsWith("\"")) {
            val = val.substring(1, val.length() - 1);
        }
        return val.isBlank() ? null : val;
    }
}
