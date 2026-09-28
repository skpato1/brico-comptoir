package tn.bricocomptoir.packs.adapter.in.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tn.bricocomptoir.packs.adapter.transaction.PackTransactions;
import tn.bricocomptoir.packs.domain.PackModels.*;

@RestController
@RequestMapping("/api/v1")
public class PackController {
    private final PackTransactions packs;
    public PackController(PackTransactions packs) { this.packs = packs; }

    @GetMapping("/packs")
    public ResponseEntity<List<PackView>> publicPacks() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(packs.packs(false).stream().map(this::view).toList());
    }
    @GetMapping("/packs/{id}")
    public ResponseEntity<PackView> publicPack(@PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(view(packs.pack(id, false)));
    }
    @GetMapping("/admin/packs/page")
    public ResponseEntity<Page<PackView>> page(@RequestParam(defaultValue="") String q,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        var result = packs.page(q,page,size);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Page<>(
                result.items().stream().map(this::view).toList(),result.page(),result.size(),result.totalElements()));
    }
    @GetMapping("/admin/packs")
    public ResponseEntity<List<PackView>> adminPacks() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(packs.packs(true).stream().map(this::view).toList());
    }
    @GetMapping("/admin/packs/{id}")
    public ResponseEntity<PackView> adminPack(@PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(view(packs.pack(id, true)));
    }
    @PostMapping("/admin/packs")
    public ResponseEntity<PackView> createPack(@Valid @RequestBody PackInput input) {
        Pack saved = packs.savePack(null, input.code(), input.name(), input.slogan(),
                input.guide(), input.status(), null);
        return ResponseEntity.created(URI.create("/api/v1/admin/packs/" + saved.id()))
                .cacheControl(CacheControl.noStore()).body(view(saved));
    }
    @PutMapping("/admin/packs/{id}")
    public PackView updatePack(@PathVariable UUID id, @Valid @RequestBody PackInput input) {
        return view(packs.savePack(id, input.code(), input.name(), input.slogan(),
                input.guide(), input.status(), input.version()));
    }
    @PostMapping("/admin/packs/{packId}/variants")
    public ResponseEntity<VariantView> createVariant(@PathVariable UUID packId,
                                                      @Valid @RequestBody VariantInput input) {
        PackVariant saved = packs.saveVariant(null, packId, input.code(), input.label(),
                input.priceTnd(), input.status(), components(input.components()), null);
        return ResponseEntity.created(URI.create("/api/v1/admin/packs/" + packId + "/variants/" + saved.id()))
                .cacheControl(CacheControl.noStore()).body(variantView(saved, packs.availability(saved)));
    }
    @PutMapping("/admin/packs/{packId}/variants/{id}")
    public VariantView updateVariant(@PathVariable UUID packId, @PathVariable UUID id,
                                     @Valid @RequestBody VariantInput input) {
        PackVariant saved = packs.saveVariant(id, packId, input.code(), input.label(),
                input.priceTnd(), input.status(), components(input.components()), input.version());
        return variantView(saved, packs.availability(saved));
    }

    private PackView view(Pack pack) {
        Map<UUID, Long> availability = packs.availability(pack);
        return new PackView(pack.id(), pack.code(), pack.name(), pack.slogan(), pack.guide(),
                pack.status(), pack.demo(), pack.version(), pack.variants().stream()
                .map(variant -> variantView(variant, availability.get(variant.id()))).toList(), packs.componentNames(pack));
    }
    private VariantView variantView(PackVariant variant, Long available) {
        return new VariantView(variant.id(), variant.code(), variant.label(),
                new MoneyView(variant.priceTnd().toPlainString(), "TND"), variant.status(),
                variant.version(), variant.components(), available);
    }
    private List<Component> components(List<ComponentInput> input) {
        if (input == null) throw new IllegalArgumentException("Components required");
        return input.stream().map(line -> line == null ? null : new Component(line.variantId(), line.quantity())).toList();
    }

    public record PackInput(@NotBlank String code, @NotBlank String name, String slogan,
                            String guide, @NotBlank String status, Long version) { }
    public record ComponentInput(@NotNull UUID variantId, long quantity) { }
    public record VariantInput(@NotBlank String code, @NotBlank String label,
                               @NotBlank String priceTnd, @NotBlank String status,
                               @NotNull List<@Valid ComponentInput> components, Long version) { }
    public record MoneyView(String amount, String currency) { }
    public record VariantView(UUID id, String code, String label, MoneyView price,
                              String status, long version, List<Component> components, Long available) { }
    public record PackView(UUID id, String code, String name, String slogan, String guide,
                           String status, boolean demo, long version, List<VariantView> variants,
                           Map<UUID, String> componentNames) { }
}
