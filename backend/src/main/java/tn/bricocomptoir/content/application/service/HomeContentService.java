package tn.bricocomptoir.content.application.service;
import tn.bricocomptoir.content.domain.HomeContent;
import tn.bricocomptoir.content.application.port.out.HomeContentStore;
public final class HomeContentService {
    private final HomeContentStore store;
    public HomeContentService(HomeContentStore store) { this.store = store; }
    public HomeContent get() { return store.get(); }
    public HomeContent save(HomeContent value, String actor) { return store.save(value.checked(), actor); }
}
