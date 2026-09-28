package tn.bricocomptoir.content.adapter.in.web;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import tn.bricocomptoir.content.domain.HomeContent;
import tn.bricocomptoir.content.adapter.transaction.HomeContentTransactions;
@RestController
@RequestMapping("/api/v1")
public class HomeContentController {
    private final HomeContentTransactions content;
    public HomeContentController(HomeContentTransactions content) { this.content = content; }
    @GetMapping({"/content/home","/admin/content/home"})
    public ResponseEntity<HomeContent> get() { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(content.get()); }
    @PutMapping("/admin/content/home")
    public HomeContent save(@RequestBody HomeContent value,@org.springframework.security.core.annotation.AuthenticationPrincipal(expression="id") java.util.UUID actor) { return content.save(value,"C:"+actor); }
}
