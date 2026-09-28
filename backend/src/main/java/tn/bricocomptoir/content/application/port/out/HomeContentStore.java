package tn.bricocomptoir.content.application.port.out;
import tn.bricocomptoir.content.domain.HomeContent;
public interface HomeContentStore {
    HomeContent get();
    HomeContent save(HomeContent content, String actor);
}
