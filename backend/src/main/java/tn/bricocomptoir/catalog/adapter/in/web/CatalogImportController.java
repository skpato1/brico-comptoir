package tn.bricocomptoir.catalog.adapter.in.web;

import java.io.IOException;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tn.bricocomptoir.catalog.adapter.transaction.CatalogTransactions;
import tn.bricocomptoir.catalog.application.port.in.CatalogImport.*;

@RestController
@RequestMapping("/api/v1/admin/catalog/imports")
public class CatalogImportController {
    private final CatalogTransactions catalog;
    public CatalogImportController(CatalogTransactions catalog) { this.catalog = catalog; }

    @PostMapping(path = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Preview> preview(@RequestPart MultipartFile file) {
        var csv = parse(file);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(catalog.previewImport(csv.rows(), csv.digest()));
    }

    @PostMapping(path = "/apply", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Applied> apply(@RequestPart MultipartFile file, @RequestParam String expectedDigest) {
        var csv = parse(file);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(catalog.applyImport(csv.rows(), csv.digest(), expectedDigest));
    }

    private static CatalogCsvParser.Parsed parse(MultipartFile file) {
        if (file.getSize() < 1 || file.getSize() > 1024 * 1024)
            throw new IllegalArgumentException("CSV must be 1 byte–1 MiB");
        try { return CatalogCsvParser.parse(file.getBytes()); }
        catch (IOException invalid) { throw new IllegalArgumentException("Cannot read CSV", invalid); }
    }
}
