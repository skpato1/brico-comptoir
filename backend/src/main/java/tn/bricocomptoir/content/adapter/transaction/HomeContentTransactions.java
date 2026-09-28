package tn.bricocomptoir.content.adapter.transaction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.bricocomptoir.content.domain.HomeContent;
import tn.bricocomptoir.content.application.service.HomeContentService;
import tn.bricocomptoir.content.application.port.out.HomeContentStore;
@Service
public class HomeContentTransactions {
    private final HomeContentService service;
    public HomeContentTransactions(HomeContentStore store) { service = new HomeContentService(store); }
    @Transactional(readOnly=true) public HomeContent get() { return service.get(); }
    @Transactional public HomeContent save(HomeContent content,String actor) { return service.save(content,actor); }
}
