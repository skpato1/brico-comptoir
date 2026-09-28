package tn.bricocomptoir.catalog.adapter.in.web;

import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import tn.bricocomptoir.catalog.application.port.in.CatalogImport.Row;

final class CatalogCsvParser {
    static final String HEADER = "productKey;categorySlug;brandSlug;productName;description;sku;variantLabel;unit;priceTnd;status";
    record Parsed(String digest, List<Row> rows) { }
    private CatalogCsvParser() { }

    static Parsed parse(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > 1024 * 1024)
            throw new IllegalArgumentException("CSV must be 1 byte–1 MiB");
        try {
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            if (text.startsWith("\uFEFF")) text = text.substring(1);
            try (CSVParser parser = CSVFormat.RFC4180.builder().setDelimiter(';').get().parse(new StringReader(text))) {
                var records = parser.getRecords();
                if (records.isEmpty() || records.getFirst().size() != 10
                        || !java.util.Arrays.equals(HEADER.split(";"), records.getFirst().values()))
                    throw new IllegalArgumentException("Unexpected CSV header");
                if (records.size() > 501) throw new IllegalArgumentException("CSV must contain at most 500 rows");
                List<Row> rows = new ArrayList<>();
                for (int i = 1; i < records.size(); i++) {
                    var record = records.get(i);
                    if (record.size() != 10) throw new IllegalArgumentException("CSV record " + (i + 1) + " must have 10 columns");
                    rows.add(new Row(i + 1, record.get(0), record.get(1), record.get(2), record.get(3),
                            record.get(4), record.get(5), record.get(6), record.get(7), record.get(8), record.get(9)));
                }
                String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
                return new Parsed(digest, List.copyOf(rows));
            }
        } catch (IllegalArgumentException invalid) { throw invalid; }
        catch (java.io.IOException | NoSuchAlgorithmException invalid) {
            throw new IllegalArgumentException("Invalid UTF-8 CSV", invalid);
        }
    }
}
