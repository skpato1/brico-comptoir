package tn.bricocomptoir.privacy.adapter.transaction;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.bricocomptoir.privacy.application.service.PrivacyService;
@Service @Transactional
public class PrivacyTransactions {
    private final PrivacyService service;
    public PrivacyTransactions(PrivacyService service) { this.service=service; }
    public PrivacyService.View view(UUID id) { return service.view(id); }
    public PrivacyService.View consent(UUID id,boolean allowed,String notice) { return service.consent(id,allowed,notice); }
    public PrivacyService.Export export(UUID id,int page) { return service.export(id,page); }
    public tn.bricocomptoir.privacy.application.port.out.PersonalData.Sales guestExport(String scope,int page) { return service.guestExport(scope,page); }
    public void requestEmail(UUID id,String email) { service.requestEmail(id,email); }
    public void confirmEmail(UUID id,String token) { service.confirmEmail(id,token); }
    public void rectify(UUID id,UUID order,String address) { service.rectify(id,order,address); }
    public void guestRectify(String scope,UUID id,String address) { service.guestRectify(scope,id,address); }
    public void anonymize(UUID id) { service.anonymize(id); }
    public void guestAnonymize(String scope) { service.guestAnonymize(scope); }
    public void hold(UUID actor,UUID order,boolean held,String reason) { service.hold(actor,order,held,reason); }
    public int purgeTokens() { return service.purgeTokens(); }
    public int retain() { return service.retain(); }
    public int retainAs(UUID actor) { return service.retainAs(actor); }
    public String archive(UUID actor,UUID order,String reference) { return service.archive(actor,order,reference); }
    public String history(UUID id,int page) { return service.history(id,page); }
}
