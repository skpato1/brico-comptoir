import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PacksComponent } from './packs';

describe('Packs', () => {
  let fixture: ComponentFixture<PacksComponent>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [PacksComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    fixture = TestBed.createComponent(PacksComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    http.expectOne('/api/v1/packs').flush([]);
  });
  afterEach(() => http.verify());

  it('renders the published variants and indicative availability returned by the server', () => {
    fixture.componentInstance.refreshPublic();
    http
      .expectOne('/api/v1/packs')
      .flush([
        {
          id: 'p-1',
          code: 'kit',
          name: 'Kit test',
          slogan: 'Exemple',
          guide: 'Guide test',
          status: 'PUBLISHED',
          demo: false,
          version: 1,
          variants: [
            {
              id: 'v-1',
              code: 'base',
              label: 'Base',
              price: { amount: '12.500', currency: 'TND' },
              status: 'PUBLISHED',
              version: 0,
              components: [{ variantId: 'sku-1', quantity: 2 }],
              available: 3,
            },
          ],
        },
      ]);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Kit test');
    expect(fixture.nativeElement.textContent).toContain('12.500 TND');
    expect(fixture.nativeElement.textContent).toContain('3 disponible(s)');
  });

  it('shows management only to catalog managers and sends the version and CSRF on update', () => {
    const component = fixture.componentInstance;
    fixture.componentRef.setInput('account', {
      id: 'c',
      email: 'c@example.tn',
      roles: ['CUSTOMER'],
      active: true,
    });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).not.toContain('Gérer les packs');
    fixture.componentRef.setInput('account', {
      id: 'm',
      email: 'm@example.tn',
      roles: ['CATALOG_MANAGER'],
      active: true,
    });
    fixture.detectChanges();
    component.toggleAdmin();
    const demo = {
      id: 'p-2',
      code: 'demo-kit',
      name: 'DÉMO Kit',
      slogan: 'Exemple',
      guide: '',
      status: 'DRAFT' as const,
      demo: true,
      version: 4,
      variants: [],
    };
    http
      .expectOne((r) => r.url === '/api/v1/admin/packs/page')
      .flush({ items: [demo], page: 0, size: 20, totalElements: 1 });
    component.editPack(demo);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('publication bloquée');
    component.packForm.name = 'DÉMO Kit modifié';
    component.savePack();
    http.expectOne('/api/v1/auth/csrf').flush(null, { status: 204, statusText: 'No Content' });
    const request = http.expectOne('/api/v1/admin/packs/p-2');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body.version).toBe(4);
    request.flush({ ...demo, name: 'DÉMO Kit modifié', version: 5 });
    http
      .expectOne((r) => r.url === '/api/v1/admin/packs/page')
      .flush({ items: [], page: 0, size: 20, totalElements: 0 });
    http.expectOne('/api/v1/packs').flush([]);
  });
});
