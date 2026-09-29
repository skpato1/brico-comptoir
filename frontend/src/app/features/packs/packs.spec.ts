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

  it('uploads pack photos with CSRF, preserves the selected pack and changes the primary image', () => {
    const component = fixture.componentInstance;
    component.adminOpen.set(true);
    fixture.componentRef.setInput('account', {
      id: 'm',
      email: 'm@example.tn',
      roles: ['CATALOG_MANAGER'],
      active: true,
    });
    const pack = {
      id: 'kit',
      code: 'kit',
      name: 'Kit test',
      slogan: '',
      guide: '',
      status: 'DRAFT' as const,
      demo: false,
      version: 0,
      variants: [],
    };
    component.editPack(pack);
    http.expectOne('/api/v1/admin/packs/kit/images').flush([]);
    const file = new File(['test'], 'kit.png', { type: 'image/png' });
    component.uploadImages({ target: { files: [file], value: 'kit.png' } } as unknown as Event);
    component.editPack({ ...pack, id: 'other' });
    expect(component.packId).toBe('kit');
    http.expectOne('/api/v1/auth/csrf').flush(null, { status: 204, statusText: 'No Content' });
    const upload = http.expectOne('/api/v1/admin/packs/kit/images');
    expect(upload.request.method).toBe('POST');
    expect(upload.request.body.getAll('files')).toHaveLength(1);
    const first = {
      id: 'photo-1',
      packId: 'kit',
      cardUrl: '/kit-card.jpg',
      detailUrl: '/kit-detail.jpg',
      width: 1600,
      height: 1000,
      sortOrder: 0,
      primary: true,
    };
    const second = { ...first, id: 'photo-2', sortOrder: 1, primary: false };
    upload.flush([first, second]);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Photos enregistrées');
    component.mainImage(second.id);
    http.expectOne('/api/v1/auth/csrf').flush(null, { status: 204, statusText: 'No Content' });
    const order = http.expectOne('/api/v1/admin/packs/kit/images/order');
    expect(order.request.method).toBe('PUT');
    expect(order.request.body).toEqual({
      imageIds: ['photo-1', 'photo-2'],
      primaryImageId: 'photo-2',
    });
    order.flush([
      { ...first, primary: false },
      { ...second, primary: true },
    ]);
    expect(component.images()[1].primary).toBe(true);
  });

  it('ignores late photos from another pack and displays a refused operation', () => {
    const component = fixture.componentInstance;
    fixture.componentRef.setInput('account', {
      id: 'm',
      email: 'm@example.tn',
      roles: ['CATALOG_MANAGER'],
      active: true,
    });
    const pack = {
      id: 'first',
      code: 'test',
      name: 'Test',
      slogan: '',
      guide: '',
      status: 'DRAFT' as const,
      demo: false,
      version: 0,
      variants: [],
    };
    component.editPack(pack);
    const previous = http.expectOne('/api/v1/admin/packs/first/images');
    component.editPack({ ...pack, id: 'second' });
    http.expectOne('/api/v1/admin/packs/second/images').flush([]);
    previous.flush([{ id: 'old-photo', packId: 'first' }]);
    expect(component.images()).toEqual([]);
    component.uploadImages({
      target: { files: [new File(['test'], 'kit.png', { type: 'image/png' })], value: '' },
    } as unknown as Event);
    http.expectOne('/api/v1/auth/csrf').flush(null, { status: 204, statusText: 'No Content' });
    http
      .expectOne('/api/v1/admin/packs/second/images')
      .flush({}, { status: 403, statusText: 'Forbidden' });
    expect(component.adminMessage()).toContain('Accès refusé');
    expect(component.busy()).toBe(false);
  });

  it('renders the published variants and indicative availability returned by the server', () => {
    fixture.componentInstance.refreshPublic();
    http.expectOne('/api/v1/packs').flush([
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
    http.expectOne('/api/v1/admin/packs/p-2/images').flush([]);
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
